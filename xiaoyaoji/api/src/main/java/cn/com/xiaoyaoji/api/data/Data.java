package cn.com.xiaoyaoji.api.data;


import java.util.List;
import java.util.Map;

import cn.com.xiaoyaoji.api.data.bean.*;
import cn.com.xiaoyaoji.api.ex.Pagination;

/**
 * @author: zhoujingjie
 * @Date: 16/5/2
 */
public interface Data {

    int insert(Object instance);

    int update(Object instance);

    int delete(Object instance);

    int delete(String tableName, String id);

    Map<String,Object> getById(String tableName, String id);

    <T> T getById(Class<T> clazz, String id);


    User login(String email, String password);

    int updateAndImage(Object instance, String... imgKeys);

    User getUserByThirdId(String thirdId);

    int bindUserWithThirdParty(Thirdparty thirdparty);


    List<Module> getModules(String projectId);

    List<Interface> getInterface(String folderId);

    int updateInterfaceFolder(String moduleId, String originalName, String newName);

    int deleteInterface(String moduleId, String folderId);

    int deleteModule(String id);

    List<InterfaceFolder> getFolders(String moduleId);

    int deleteInterfaceFolder(String id);

    List<Team> getTeams(String userId);

    List<Project> getProjects(String userId);

    List<User> getUsersByProjectId(String projectId);

    List<User> getAllProjectUsersByUserId(String userId);

    int deleteTeam(String id);

    int deleteProject(String id);

    List<User> searchUsers(String key,String... excludeIds);

    boolean checkEmailExists(String email);

    boolean checkProjectUserExists(String projectId, String userId);

    int deleteProjectUser(String projectId, String userId);

    List<Interface> getInterfacesByModuleId(String moduleId);

    List<InterfaceFolder> getFoldersByProjectId(String projectId);

    List<Interface> getInterfacesByProjectId(String projectId);

    String getUserIdByEmail(String email);

    int findPassword(String id, String email, String password);

    boolean checkUserHasProjectPermission(String userId, String projectId);

    int importFromRap(Project project, List<Module> modules, List<InterfaceFolder> folders, List<Interface> interfaces);

    void initUserThirdlyBinds(User user);

    int copyFolder(String folderId, String moduleId);

    int removeUserThirdPartyRelation(String userId, String type);

    int createProject(Project project);

    String getProjectName(String projectId);

    String getInterfaceFolderName(String folderId);

    String getModuleName(String moduleId);

    String getInterfaceName(String interfaceId);

    String getProjectEditable(String projectId, String userId);

    int updateProjectUserEditable(String projectId, String userId, String editable);

    int updateCommonlyUsedProject(String projectId, String userId, String isCommonlyUsed);

    List<Module> getModules(String[] moduleIdsArray);

    List<InterfaceFolder> getFoldersByModuleIds(String[] moduleIds);

    List<Interface> getInterfacesByModuleIds(String[] moduleIds);

    List<Share> getSharesByProjectId(String projectId);

    List<Map<String,Object>> getModuleNameIdsInIds(String[] moduleIdsArray);

    int updateFolderSorts(String[] idsorts);

    int updateInterfaceSorts(String[] idsorts);

    String getUserName(String userId);

    List<ProjectLog> getProjectLogs(Pagination pagination);

    int importFromMJSON(Project project, List<Module> moduleList);

    int moveFolder(String folderId,String newModuleId);

    //void test();
}
