package cn.com.xiaoyaoji.api.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.mangoframework.core.annotation.*;
import org.mangoframework.core.dispatcher.Parameter;
import org.mangoframework.core.exception.InvalidArgumentException;

import cn.com.xiaoyaoji.api.annotations.Ignore;
import cn.com.xiaoyaoji.api.asynctask.AsyncTaskBus;
import cn.com.xiaoyaoji.api.asynctask.log.Log;
import cn.com.xiaoyaoji.api.asynctask.message.MessageBus;
import cn.com.xiaoyaoji.api.data.bean.*;
import cn.com.xiaoyaoji.api.ex.Handler;
import cn.com.xiaoyaoji.api.ex.Message;
import cn.com.xiaoyaoji.api.ex._HashMap;
import cn.com.xiaoyaoji.api.service.ServiceFactory;
import cn.com.xiaoyaoji.api.service.ServiceTool;
import cn.com.xiaoyaoji.api.utils.*;
import cn.com.xiaoyaoji.api.view.PdfView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

/**
 * //todo 权限验证
 * 项目
 *
 * @author zhoujingjie
 * @date 2016-07-20
 */
@RequestMapping("/project")
public class ProjectController {
    private static Logger logger = Logger.getLogger(ProjectController.class);

    @Get("list")
    public Object list(Parameter parameter) {
        User user = MemoryUtils.getUser(parameter.getParamString().get("token"));
        List<Project> projects = new ArrayList<>();
        if (user != null) {
            String userId = user.getId();
            projects = ServiceFactory.instance().getProjects(userId);
        }
        return new _HashMap<>().add("projects", projects);

    }

    /**
     * 查询单个module对应的接口
     *
     * @param id
     * @return
     */
    @Ignore
    @Get(value = "{id}", template = "/api")
    public Map<String, Object> get(@RequestParam("id") String id, Parameter parameter) {
        Project project = ServiceFactory.instance().getProject(id);
        if (project == null || !Project.Status.VALID.equals(project.getStatus())) {
            return new _HashMap<>();
        }
        User user = MemoryUtils.getUser(parameter);
        if (project.getPermission().equals(Project.Permission.PRIVATE)) {
            AssertUtils.isTrue(user != null, "无访问权限");
            if (!user.getId().equals(project.getUserId())) {
                // 检查用户是否有访问权限
                AssertUtils.isTrue(ServiceFactory.instance().checkProjectUserExists(project.getId(), user.getId()), "无访问权限");
            }
        }
        if (user != null) {
            project.setEditable(ServiceFactory.instance().getProjectEditable(project.getId(), user.getId()));
        } else {
            project.setEditable(ProjectUser.Editable.NO);
        }
        if (project.getId().equalsIgnoreCase("demo")) {
            project.setEditable(ProjectUser.Editable.YES);
        }
        List<Module> modules = ServiceFactory.instance().getModules(id);
        List<InterfaceFolder> folders = null;
        if (modules.size() > 0) {
            // 获取该项目下所有文件夹
            folders = ServiceFactory.instance().getFoldersByProjectId(project.getId());
            Map<String, List<InterfaceFolder>> folderMap = ResultUtils.listToMap(folders, new Handler<InterfaceFolder>() {
                @Override
                public String key(InterfaceFolder item) {
                    return item.getModuleId();
                }
            });
            for (Module module : modules) {
                List<InterfaceFolder> temp = folderMap.get(module.getId());
                if (temp != null) {
                    module.setFolders(temp);
                }
            }

            // 获取该项目下所有接口
            List<Interface> interfaces = ServiceFactory.instance().getInterfacesByProjectId(project.getId());
            Map<String, List<Interface>> interMap = ResultUtils.listToMap(interfaces, new Handler<Interface>() {
                @Override
                public String key(Interface item) {
                    return item.getFolderId();
                }
            });
            for (InterfaceFolder folder : folders) {
                List<Interface> temp = interMap.get(folder.getId());
                if (temp != null) {
                    folder.setChildren(temp);
                }
            }
        } else {
            String token = parameter.getParamString().get("token");
            Module module = createDefaultModule(token, project.getId());
            modules = new ArrayList<>();
            modules.add(module);
        }
        return new _HashMap<String, Object>().add("modules", modules).add("project", project);
    }

    /**
     * 导出成json
     * @param id
     * @param parameter
     * @return
     */
    @Get(value = "/{id}/exportmjson")
    public Object export2JSON(@RequestParam("id") String id, Parameter parameter) {
        parameter.getResponse().setHeader("Content-Disposition", "attachment; filename=\"" + id + ".mjson\"");
        Map<String, Object> map = get(id, parameter);
        AssertUtils.isTrue(map.size() > 0, "项目不存在或无效");
        return map;
    }

    /**
     * 导出成json
     * @param parameter
     * @return
     */
    @Post(value = "/importmjson")
    public Object importFromJSON(Parameter parameter) {
        List<FileItem> items = parameter.getParamFile().get("mjson");
        AssertUtils.isTrue(items != null && items.size() > 0, "请上传mjson文件");
        try {
            String json = IOUtils.toString(items.get(0).getInputStream(), "UTF-8");
            JSONObject obj = JSON.parseObject(json);
            Project project = obj.getObject("project", Project.class);
            project.setUserId(MemoryUtils.getUser(parameter).getId());
            JSONArray modules = obj.getJSONArray("modules");
            List<Module> moduleList = new ArrayList<>(modules.size());
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.getObject(i, Module.class);
                moduleList.add(module);
            }
            int rs = ServiceFactory.instance().importFromMJSON(project, moduleList);
            AssertUtils.isTrue(rs > 0, "导入失败");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new InvalidArgumentException("json格式错误或导入错误");
        }
        return true;
    }

    @Get("/{id}/info")
    public Object info(@RequestParam("id") String id) {
        Project project = ServiceFactory.instance().getProject(id);
        AssertUtils.isTrue(project != null, "project is null");
        return new _HashMap<>().add("project", project);
    }

    @Get("/{id}/shares")
    public Object shares(@RequestParam("id") String id) {
        return new _HashMap<>().add("shares", ServiceFactory.instance().getSharesByProjectId(id));
    }

    /**
     * 设置是否常用项目
     * @param id
     * @param parameter
     * @return
     */
    @Post("/{id}/commonly")
    public int updateCommonlyUsed(@RequestParam("id") String id, Parameter parameter) {
        String userId = MemoryUtils.getUser(parameter).getId();
        String isCommonlyUsed = parameter.getParamString().get("isCommonlyUsed");
        AssertUtils.notNull(isCommonlyUsed, "isCommonlyUsed is null");
        int rs = ServiceFactory.instance().updateCommonlyUsedProject(id, userId, isCommonlyUsed);
        AssertUtils.isTrue(rs > 0, "操作失败");
        return rs;
    }

    // 创建默认模块
    private Module createDefaultModule(String token, String projectId) {
        Module module = new Module();
        module.setLastUpdateTime(new Date());
        module.setCreateTime(new Date());
        module.setId(StringUtils.id());
        module.setProjectId(projectId);
        module.setName("默认模块");
        int rs = ServiceFactory.instance().create(module);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        module.addInterfaceFolder(createDefaultFolder(module.getId(), projectId, token));
        return module;
    }

    // 创建默认分类
    private InterfaceFolder createDefaultFolder(String moduleId, String projectId, String token) {
        InterfaceFolder folder = new InterfaceFolder();
        folder.setId(StringUtils.id());
        folder.setName("默认分类");
        folder.setCreateTime(new Date());
        folder.setModuleId(moduleId);
        folder.setProjectId(projectId);
        int rs = ServiceFactory.instance().create(folder);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        return folder;
    }

    @Get("/{id}/users")
    public Object getUsers(@RequestParam("id") String id, Parameter parameter) {
        List<User> users = ServiceFactory.instance().getUsersByProjectId(id);
        return new _HashMap<>().add("users", users).add("fileAccess", ConfigUtils.getFileAccessURL());
    }

    @Post
    public Object create(Parameter parameter) {
        String token = parameter.getParamString().get("token");
        User user = MemoryUtils.getUser(token);
        Project project = BeanUtils.convert(Project.class, parameter.getParamString());
        project.setId(StringUtils.id());
        project.setCreateTime(new Date());
        project.setUserId(user.getId());
        project.setStatus(Project.Status.VALID);
        project.setEditable(ProjectUser.Editable.YES);
        AssertUtils.notNull(project.getName(), "missing name");
        // AssertUtils.notNull(project.getTeamId(),"missing teamId");
        AssertUtils.notNull(project.getUserId(), "missing userId");
        int rs = ServiceFactory.instance().createProject(project);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        return project.getId();
    }

    private String diffOperation(Project before, Project now) {
        StringBuilder sb = new StringBuilder();
        if (ServiceTool.modified(before.getName(), now.getName())) {
            sb.append("名称,");
        }
        if (ServiceTool.modified(before.getDescription(), now.getDescription())) {
            sb.append("描述,");
        }
        if (ServiceTool.modified(before.getPermission(), now.getPermission())) {
            sb.append("项目状态,");
        }
        if (ServiceTool.modified(before.getEnvironments(), now.getEnvironments())) {
            sb.append("环境变量,");
        }
        if (ServiceTool.modified(before.getDetails(), now.getDetails())) {
            sb.append("文档说明,");
        }

        if (sb.length() > 0) {
            sb = sb.delete(sb.length() - 1, sb.length());
        }
        return sb.toString();
    }

    /**
     * 更新
     * @param id
     * @param parameter
     * @return
     */
    @Post("{id}")
    public Object update(@RequestParam("id") String id, Parameter parameter) {
        String token = parameter.getParamString().get("token");
        Project before = ServiceFactory.instance().getProject(id);
        ServiceTool.checkUserHasEditPermission(id, parameter);
        //
        Project project = BeanUtils.convert(Project.class, parameter.getParamString());
        project.setId(id);
        project.setUserId(null);
        int rs = ServiceFactory.instance().update(project);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        AsyncTaskBus.instance().push(id, Log.UPDATE_PROJECT, id, token, "修改项目-" + before.getName() + "-" + diffOperation(before, project));
        return rs;
    }

    /**
     * 项目转让
     * @param id
     * @param parameter
     * @return
     */
    @Post("/{id}/transfer")
    public Object transfer(@RequestParam("id") String id, Parameter parameter) {
        String userId = parameter.getParamString().get("userId");
        String token = parameter.getParamString().get("token");
        AssertUtils.isTrue(org.apache.commons.lang3.StringUtils.isNoneBlank(userId), "missing userId");
        Project before = ServiceFactory.instance().getProject(id);
        ServiceTool.checkUserHasOperatePermission(before, parameter);
        Project temp = new Project();
        temp.setId(id);
        temp.setUserId(userId);
        int rs = ServiceFactory.instance().update(temp);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        String userName = ServiceFactory.instance().getUserName(userId);
        AsyncTaskBus.instance().push(id, Log.TRANSFER_PROJECT, id, token, "转让项目给" + userName);
        return rs;
    }

    /**
     * 删除项目
     * @param id
     * @param parameter
     * @return
     */
    @Delete("{id}")
    public Object delete(@RequestParam("id") String id, Parameter parameter) {
        Project before = ServiceFactory.instance().getProject(id);
        ServiceTool.checkUserHasEditPermission(id, parameter);
        String token = parameter.getParamString().get("token");
        int rs = ServiceFactory.instance().deleteProject(id);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        // AsyncTaskBus.instance().push(id,Log.DELETE_PROJECT,id,token,"删除项目-"+before.getName());
        return rs;
    }

    @Post("save")
    public Object save(Parameter parameter) {
        String id = parameter.getParamString().get("id");
        if (org.apache.commons.lang3.StringUtils.isEmpty(id)) {
            return create(parameter);
        }
        return update(id, parameter);
    }

    /**
     * 移动复制
     * @param id
     * @param parameter
     * @return
     */
    @Post("/{id}/copymove")
    public int copyMove(@RequestParam("id") String id, Parameter parameter) {
        // todo 复制移动
        AssertUtils.notNull(parameter, "action", "moduleId", "type", "targetId");
        // 动作
        String action = parameter.getParamString().get("action");
        // 类型
        String type = parameter.getParamString().get("type");
        //
        String moduleId = parameter.getParamString().get("moduleId");
        //
        String folderId = parameter.getParamString().get("folderId");
        //
        String targetId = parameter.getParamString().get("targetId");
        //
        String projectId = parameter.getParamString().get("projectId");
        if (type.equals("api")) {
            AssertUtils.notNull(folderId, "missing folderId");
        }
        String token = parameter.getParamString().get("token");
        int rs = 0;
        if (action.equals("move")) {
            // 移动
            if (type.equals("folder")) {
                rs = ServiceFactory.instance().moveFolder(targetId,moduleId);
                // String folderName = ServiceFactory.instance().getInterfaceFolderName(targetId);
                // AsyncTaskBus.instance().push(Log.create(token, Log.MOVE_FOLDER,folderName,id));
            } else {
                Interface in = new Interface();
                in.setId(targetId);
                in.setModuleId(moduleId);
                in.setFolderId(folderId);
                rs = ServiceFactory.instance().update(in);

                // String interfaceName = ServiceFactory.instance().getInterfaceName(targetId);
                // AsyncTaskBus.instance().push(Log.create(token, Log.MOVE_INTERFACE,interfaceName,id));
            }
        } else if (action.equals("copy")) {
            // 复制
            if (type.equals("folder")) {
                rs = ServiceFactory.instance().copyFolder(targetId, moduleId);
                // String folderName = ServiceFactory.instance().getInterfaceFolderName(targetId);
                // AsyncTaskBus.instance().push(Log.create(token, Log.COPY_FOLDER,folderName,id));
            } else {
                // 接口
                Interface in = ServiceFactory.instance().getById(targetId, Interface.class);
                AssertUtils.notNull(in, "无效接口Id");
                in.setId(StringUtils.id());
                in.setFolderId(folderId);
                in.setModuleId(moduleId);
                in.setCreateTime(new Date());
                in.setLastUpdateTime(new Date());
                if (in.getName() == null) {
                    in.setName("");
                }
                if (!in.getName().contains("COPY")) {
                    in.setName(in.getName() + "_COPY");
                }
                rs = ServiceFactory.instance().create(in);
                AsyncTaskBus.instance().push(projectId, Project.Action.COPY_INTERFACE, in.getId(), token, in.getFolderId());
                // String interfaceName = ServiceFactory.instance().getInterfaceName(targetId);
                // AsyncTaskBus.instance().push(Log.create(token, Log.COPY_INTERFACE,interfaceName,id));
            }
        }
        AssertUtils.isTrue(rs > 0, "操作失败");
        return rs;
    }

    /**
     * 邀请成员
     *
     * @param id
     * @param parameter
     * @return
     */
    @Post("/{id}/invite")
    public String invite(@RequestParam("id") String id, Parameter parameter) {
        User user = MemoryUtils.getUser(parameter);
        ProjectUser pu = new ProjectUser();
        pu.setId(StringUtils.id());
        pu.setUserId(parameter.getParamString().get("userId"));
        AssertUtils.isTrue(org.apache.commons.lang3.StringUtils.isNotBlank(pu.getUserId()), "missing userId");
        AssertUtils.isTrue(!ServiceFactory.instance().checkProjectUserExists(id, pu.getUserId()), "用户已存在该项目中");
        AssertUtils.isTrue(!pu.getUserId().equals(user.getId()), "不能邀请自己");
        pu.setCreateTime(new Date());
        pu.setStatus(ProjectUser.Status.PENDING);
        pu.setEditable(ProjectUser.Editable.NO);
        pu.setProjectId(id);
        int rs = ServiceFactory.instance().create(pu);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        MessageBus.instance().push("PROJECT.INVITE", pu.getProjectId(), new String[] { pu.getUserId() });
        return pu.getId();
    }

    /**
     * 邀请成员
     *
     * @param id
     * @param parameter
     * @return
     */
    @Post("/{id}/invite/email")
    public String inviteByEmail(@RequestParam("id") String id, Parameter parameter) {
        String email = parameter.getParamString().get("email");
        String userId = ServiceFactory.instance().getUserIdByEmail(email);
        AssertUtils.isTrue(userId != null, "该邮箱未注册");
        User user = MemoryUtils.getUser(parameter);
        AssertUtils.isTrue(!userId.equals(user.getId()), "不能邀请自己");
        AssertUtils.isTrue(!ServiceFactory.instance().checkProjectUserExists(id, userId), "用户已存在该项目中");

        ProjectUser pu = new ProjectUser();
        pu.setId(StringUtils.id());
        pu.setUserId(userId);
        pu.setProjectId(id);
        pu.setEditable(ProjectUser.Editable.YES);
        AssertUtils.isTrue(org.apache.commons.lang3.StringUtils.isNotBlank(pu.getProjectId()), "missing projectId");
        pu.setCreateTime(new Date());
        pu.setStatus(ProjectUser.Status.PENDING);
        int rs = ServiceFactory.instance().create(pu);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        MessageBus.instance().push("PROJECT.INVITE", pu.getProjectId(), new String[] { pu.getUserId() });
        return pu.getId();
    }

    /**
     * 接受邀请
     *
     * @param inviteId
     * @return
     */
    @Post("/{id}/pu/{inviteId}/accept")
    public int acceptInvite(@RequestParam("inviteId") String inviteId) {
        ProjectUser pu = new ProjectUser();
        pu.setId(inviteId);
        pu.setStatus(ProjectUser.Status.ACCEPTED);
        int rs = ServiceFactory.instance().create(pu);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        return rs;
    }

    /**
     * 拒绝邀请
     */
    @Post("/{id}/pu/{inviteId}/refuse")
    public int acceptRefuse(@RequestParam("inviteId") String inviteId) {
        ProjectUser pu = new ProjectUser();
        pu.setId(inviteId);
        pu.setStatus(ProjectUser.Status.REFUSED);
        int rs = ServiceFactory.instance().create(pu);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        MessageBus.instance().push("PROJECT.INVITE.REFUSE", pu.getProjectId(), pu.getUserId());
        return rs;
    }

    /**
     * 移除成员
     *
     * @param userId    userId
     * @param id projectId
     * @param parameter
     * @return
     */
    @Delete("/{id}/pu/{userId}")
    public int removeMember(@RequestParam("id") String id, @RequestParam("userId") String userId, Parameter parameter) {
        Project project = ServiceFactory.instance().getProject(id);
        ServiceTool.checkUserHasOperatePermission(project, parameter);
        AssertUtils.isTrue(!project.getUserId().equals(userId), "不能移除自己");
        User temp = MemoryUtils.getUser(parameter);
        AssertUtils.isTrue(temp.getId().equals(project.getUserId()), "无操作权限");
        int rs = ServiceFactory.instance().deleteProjectUser(id, userId);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        return rs;
    }

    /**
     * 设置是否可编辑
     * @param projectId 项目id
     * @param userId
     * @param editable
     * @param parameter
     * @return
     */
    @Post("/{id}/pu/{userId}/{editable}")
    public int editProjectEditable(@RequestParam("id") String projectId, @RequestParam("userId") String userId, @RequestParam("editable") String editable,
            Parameter parameter) {
        AssertUtils.isTrue(ProjectUser.Editable.YES.equals(editable) || ProjectUser.Editable.NO.equals(editable), "参数错误");
        Project project = ServiceFactory.instance().getProject(projectId);
        ServiceTool.checkUserHasOperatePermission(project, parameter);
        AssertUtils.isTrue(!project.getUserId().equals(userId), "项目所有人不能修改自己的权限");
        int rs = ServiceFactory.instance().updateProjectUserEditable(projectId, userId, editable);
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        return rs;
    }

    /**
     * 退出项目
     * @param id
     * @param parameter
     * @return
     */
    @Delete("/{id}/quit")
    public int quit(@RequestParam("id") String id, Parameter parameter) {
        String token = parameter.getParamString().get("token");
        Project project = ServiceFactory.instance().getProject(id);
        AssertUtils.notNull(project, "project not exists");
        String userId = MemoryUtils.getUser(parameter).getId();
        AssertUtils.isTrue(!project.getUserId().equals(userId), "项目所有人不能退出项目");
        int rs = ServiceFactory.instance().deleteProjectUser(id, userId);
        AsyncTaskBus.instance().push(id, Log.QUIT_PROJECT, id, token, "退出项目");
        AssertUtils.isTrue(rs > 0, Message.OPER_ERR);
        return rs;
    }

    /**
     * 项目导出
     * @param parameter
     * @param id
     * @return
     * @throws DocumentException
     * @throws IOException
     */
    @Get("/{id}/export")
    public Object export(Parameter parameter, @RequestParam("id") String id) throws DocumentException, IOException {
        String token = parameter.getParamString().get("token");
        Project project = ServiceFactory.instance().getProject(id);
        List<Module> modules = getModulesByProjectId(project, token);
        AssertUtils.notNull(modules, "该项目不存在或已下线");
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setViewerPreferences(PdfWriter.PageModeUseOutlines);
            document.open();
            // 方正兰亭
            BaseFont font = BaseFont.createFont("FZLTCXHJW.TTF", BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
            Font moduleFont = new Font(font, 24f, Font.BOLD, BaseColor.BLACK);
            Font folderFont = new Font(font, 20f, Font.BOLD, new BaseColor(66, 66, 66));
            Font apiName = new Font(font, 18f, Font.BOLD, new BaseColor(66, 66, 66));
            Font subtitle = new Font(font, 14f, Font.BOLD, BaseColor.BLACK);
            Font apiFont = new Font(font, 10f, Font.BOLD, new BaseColor(66, 66, 66));
            Paragraph temp = new Paragraph(project.getName(), new Font(font, 32f, Font.BOLD, BaseColor.BLACK));
            document.add(Chunk.NEWLINE);
            temp.setAlignment(Element.ALIGN_CENTER);
            document.add(temp);
            if (org.apache.commons.lang3.StringUtils.isNotBlank(project.getDetails())) {
                document.add(new Paragraph(project.getDetails(), apiFont));
            }
            if (org.apache.commons.lang3.StringUtils.isNoneBlank(project.getEnvironments())) {
                try {
                    JSONArray arr = JSON.parseArray(project.getEnvironments());
                    if (arr.size() > 0) {
                        document.add(new Paragraph("全局环境变量", apiName));
                        document.add(new Paragraph("环境变量运行在URL中,你可以配置多个(线上、灰度、开发)环境变量。在URL中使用方式$变量名$,例：\n" + "线上环境：prefix => http://www.xiaoyaoji.com.cn\n"
                                + "则\n" + "请求URL：$prefix$/say => http://www.xiaoyaoji.com.cn/say\n\n", apiFont));
                        for (int i = 0; i < arr.size(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            String environmentName = item.getString("name");
                            Paragraph p = new Paragraph(environmentName, subtitle);
                            p.setPaddingTop(10);
                            document.add(p);
                            try {
                                JSONArray vars = item.getJSONArray("vars");
                                if (vars.size() > 0) {
                                    for (int v = 0; v < vars.size(); v++) {
                                        JSONObject var = vars.getJSONObject(v);
                                        document.add(new Paragraph(var.getString("name") + "    " + var.getString("value"), apiFont));
                                    }
                                }
                            } catch (Exception e) {
                                // e.printStackTrace();
                            }
                        }
                    }
                } catch (Exception e) {
                    // e.printStackTrace();
                }
            }
            for (int m = 0; m < modules.size(); m++) {
                Module module = modules.get(m);
                document.add(new Paragraph(module.getName(), moduleFont));
                createUpdateTimeCell(document, DateUtils.toStr(module.getLastUpdateTime()), apiFont);

                Paragraph cTitle = new Paragraph(module.getName(), moduleFont);
                Chapter chapter = new Chapter(cTitle, m + 1);
                List<InterfaceFolder> folders = module.getFolders();
                for (int f = 0; f < folders.size(); f++) {
                    InterfaceFolder folder = folders.get(f);
                    Section section = chapter.addSection(new Paragraph(folder.getName(), folderFont));
                    section.setIndentationRight(0);
                    section.setIndentationLeft(0);
                    if (m > 2) {
                        section.setBookmarkOpen(false);
                    }
                    // section.add(new Paragraph());
                    List<Interface> ins = folder.getChildren();
                    for (int i = 0; i < ins.size(); i++) {
                        Interface in = ins.get(i);
                        section.addSection(new Paragraph(in.getName(), apiName));
                        section.add(new Paragraph("基本信息", subtitle));
                        createUpdateTimeCell(section, DateUtils.toStr(in.getLastUpdateTime()), apiFont);
                        section.add(new Paragraph("请求类型：" + in.getProtocol(), apiFont));
                        section.add(new Paragraph("请求地址：" + in.getUrl(), apiFont));
                        if ("HTTP".equals(in.getProtocol())) {
                            section.add(new Paragraph("请求方式：" + in.getRequestMethod(), apiFont));
                            section.add(new Paragraph("数据类型：" + in.getDataType(), apiFont));
                            section.add(new Paragraph("响应类型：" + in.getContentType(), apiFont));
                        }
                        section.add(new Paragraph(in.getDescription(), apiFont));

                        // 全局请求头
                        String globalRequestHeaders = module.getRequestHeaders();
                        if (org.apache.commons.lang3.StringUtils.isNotBlank(globalRequestHeaders)) {
                            List<RequestResponseArgs> requestHeaders = JSON.parseObject(globalRequestHeaders, new TypeReference<List<RequestResponseArgs>>() {
                            });
                            if (requestHeaders.size() > 0) {
                                Paragraph gp = new Paragraph("全局请求头", subtitle);
                                section.add(gp);
                                PdfPTable table = new PdfPTable(4);
                                decorateTable(table);
                                table.addCell(createHeaderCell("参数名称", apiFont));
                                table.addCell(createHeaderCell("是否必须", apiFont));
                                table.addCell(createHeaderCell("描述", apiFont));
                                table.addCell(createHeaderCell("默认值", apiFont));
                                addCells(table, requestHeaders, "requestHeaders", apiFont, 0);
                                section.add(table);
                            }
                        }
                        // 请求头
                        String requestHeader = in.getRequestHeaders();
                        if (org.apache.commons.lang3.StringUtils.isNotBlank(requestHeader)) {
                            List<RequestResponseArgs> requestHeaders = JSON.parseObject(requestHeader, new TypeReference<List<RequestResponseArgs>>() {
                            });
                            if (requestHeaders.size() > 0) {
                                Paragraph p = new Paragraph("请求头", subtitle);
                                section.add(p);
                                PdfPTable table = new PdfPTable(4);
                                decorateTable(table);
                                table.addCell(createHeaderCell("参数名称", apiFont));
                                table.addCell(createHeaderCell("是否必须", apiFont));
                                table.addCell(createHeaderCell("描述", apiFont));
                                table.addCell(createHeaderCell("默认值", apiFont));
                                addCells(table, requestHeaders, "requestHeaders", apiFont, 0);
                                section.add(table);
                            }
                        }
                        // 全局请求参数
                        String globalRequestArg = module.getRequestArgs();
                        if (org.apache.commons.lang3.StringUtils.isNotBlank(globalRequestArg)) {
                            List<RequestResponseArgs> requestArgs = JSON.parseObject(globalRequestArg, new TypeReference<List<RequestResponseArgs>>() {
                            });
                            if (requestArgs.size() > 0) {
                                section.add(new Paragraph("全局请求参数", subtitle));
                                PdfPTable table = new PdfPTable(5);
                                decorateTable(table);
                                table.addCell(createHeaderCell("参数名称", apiFont));
                                table.addCell(createHeaderCell("是否必须", apiFont));
                                table.addCell(createHeaderCell("类型", apiFont));
                                table.addCell(createHeaderCell("描述", apiFont));
                                table.addCell(createHeaderCell("默认值", apiFont));
                                addCells(table, requestArgs, "requestArgs", apiFont, 0);
                                section.add(table);
                            }
                        }

                        // 请求参数
                        String requestArg = in.getRequestArgs();
                        if (org.apache.commons.lang3.StringUtils.isNotBlank(requestArg)) {
                            List<RequestResponseArgs> requestArgs = JSON.parseObject(requestArg, new TypeReference<List<RequestResponseArgs>>() {
                            });
                            if (requestArgs.size() > 0) {
                                section.add(new Paragraph("请求参数", subtitle));
                                PdfPTable table = new PdfPTable(5);
                                decorateTable(table);
                                table.addCell(createHeaderCell("参数名称", apiFont));
                                table.addCell(createHeaderCell("是否必须", apiFont));
                                table.addCell(createHeaderCell("类型", apiFont));
                                table.addCell(createHeaderCell("描述", apiFont));
                                table.addCell(createHeaderCell("默认值", apiFont));
                                addCells(table, requestArgs, "requestArgs", apiFont, 0);
                                section.add(table);
                            }
                        }
                        // 响应参数
                        String responseArg = in.getResponseArgs();
                        if (org.apache.commons.lang3.StringUtils.isNotBlank(responseArg)) {
                            List<RequestResponseArgs> responseArgs = JSON.parseObject(responseArg, new TypeReference<List<RequestResponseArgs>>() {
                            });
                            if (responseArgs.size() > 0) {
                                section.add(new Paragraph("响应数据", subtitle));
                                PdfPTable table = new PdfPTable(4);
                                decorateTable(table);
                                table.addCell(createHeaderCell("参数名称", apiFont));
                                table.addCell(createHeaderCell("是否必须", apiFont));
                                table.addCell(createHeaderCell("数据类型", apiFont));
                                table.addCell(createHeaderCell("描述", apiFont));
                                addCells(table, responseArgs, "responseArgs", apiFont, 0);
                                section.add(table);
                            }
                        }

                        if (org.apache.commons.lang3.StringUtils.isNotBlank(in.getExample())) {
                            section.add(new Paragraph("例子", subtitle));
                            section.add(new Paragraph(in.getExample()));
                        }

                        // document.newPage();
                        section.add(Chunk.NEWLINE);
                        section.add(Chunk.NEWLINE);
                        section.add(Chunk.NEWLINE);
                        // section.newPage();
                    }
                }
                document.add(chapter);
            }
            document.close();
            byte[] data = baos.toByteArray();
            return new PdfView(data, project.getName() + ".pdf");
        } finally {
            // document.close();
            IOUtils.closeQuietly(baos);
        }
    }

    // 创建时间更新
    private void createUpdateTimeCell(Document document, String text, Font font) throws DocumentException {
        Paragraph lastUpdateTime = new Paragraph("更新时间:" + text, font);
        lastUpdateTime.setIndentationRight(0);
        lastUpdateTime.setPaddingTop(0);
        lastUpdateTime.setAlignment(Element.ALIGN_RIGHT);
        document.add(lastUpdateTime);
    }

    // 创建更新时间
    private void createUpdateTimeCell(Section element, String text, Font font) throws DocumentException {
        Paragraph lastUpdateTime = new Paragraph("更新时间:" + text, font);
        lastUpdateTime.setIndentationRight(0);
        lastUpdateTime.setPaddingTop(0);
        lastUpdateTime.setAlignment(Element.ALIGN_RIGHT);
        element.add(lastUpdateTime);
    }

    // 创建table 列
    private PdfPCell createCell(String text, Font font) {
        if (text == null)
            text = " ";
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        return cell;
    }

    // 创建table头
    private PdfPCell createHeaderCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(8);
        cell.setBackgroundColor(new BaseColor(204, 204, 204));
        return cell;
    }

    // 添加列
    private void addCells(PdfPTable table, List<RequestResponseArgs> list, String type, Font font, int i) {
        for (RequestResponseArgs item : list) {
            table.addCell(createCell(getBlank(i) + item.getName(), font));
            if (org.apache.commons.lang3.StringUtils.isBlank(item.getRequire())) {
                table.addCell(createCell("false", font));
            } else {
                table.addCell(createCell(item.getRequire(), font));
                // table.addCell(new Phrase(getText(item.getRequire()),font));
            }
            if (type.equals("requestArgs") || type.equals("responseArgs")) {
                table.addCell(createCell(item.getType(), font));
            }
            table.addCell(createCell(item.getDescription(), font));
            if (!type.equals("responseArgs")) {
                table.addCell(createCell(item.getDefaultValue(), font));
            }
            if (item.getChildren().size() > 0) {
                int temp = i + 1;
                addCells(table, item.getChildren(), type, font, temp);
            }
        }
    }

    // 设置table样式
    private void decorateTable(PdfPTable table) {
        table.setHeaderRows(1);
        // table.setFooterRows(1);
        table.setComplete(true);
        // table.setSkipFirstHeader(true);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        table.setSpacingBefore(10);
    }

    private java.util.List<Module> getModulesByProjectId(Project project, String token) {

        if (project == null || !Project.Status.VALID.equals(project.getStatus())) {
            return null;
        }
        boolean has = ServiceFactory.instance().checkUserHasProjectPermission(MemoryUtils.getUser(token).getId(), project.getId());
        AssertUtils.isTrue(has, "无访问权限");
        java.util.List<Module> modules = ServiceFactory.instance().getModules(project.getId());
        java.util.List<InterfaceFolder> folders = null;
        if (modules.size() > 0) {
            // 获取该项目下所有文件夹
            folders = ServiceFactory.instance().getFoldersByProjectId(project.getId());
            Map<String, java.util.List<InterfaceFolder>> folderMap = ResultUtils.listToMap(folders, new Handler<InterfaceFolder>() {
                @Override
                public String key(InterfaceFolder item) {
                    return item.getModuleId();
                }
            });
            for (Module module : modules) {
                java.util.List<InterfaceFolder> temp = folderMap.get(module.getId());
                if (temp != null) {
                    module.setFolders(temp);
                }
            }

            // 获取该项目下所有接口
            java.util.List<Interface> interfaces = ServiceFactory.instance().getInterfacesByProjectId(project.getId());
            Map<String, java.util.List<Interface>> interMap = ResultUtils.listToMap(interfaces, new Handler<Interface>() {
                @Override
                public String key(Interface item) {
                    return item.getFolderId();
                }
            });
            for (InterfaceFolder folder : folders) {
                java.util.List<Interface> temp = interMap.get(folder.getId());
                if (temp != null) {
                    folder.setChildren(temp);
                }
            }
        }
        return modules;
    }

    private String getBlank(int index) {
        if (index == 0)
            return "";
        return org.apache.commons.lang3.StringUtils.repeat("    ", index);
    }
}
