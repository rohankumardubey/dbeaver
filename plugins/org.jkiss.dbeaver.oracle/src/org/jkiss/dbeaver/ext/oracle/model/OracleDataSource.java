/*
 * Copyright (c) 2012, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ext.oracle.model;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.IAdaptable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.ext.oracle.OracleDataSourceProvider;
import org.jkiss.dbeaver.ext.oracle.model.plan.OraclePlanAnalyser;
import org.jkiss.dbeaver.ext.oracle.oci.OCIClassLoader;
import org.jkiss.dbeaver.ext.oracle.oci.OCIUtils;
import org.jkiss.dbeaver.ext.oracle.oci.OracleHomeDescriptor;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.jdbc.*;
import org.jkiss.dbeaver.model.exec.plan.DBCPlan;
import org.jkiss.dbeaver.model.exec.plan.DBCQueryPlanner;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSource;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSourceInfo;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCStructCache;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.*;
import org.jkiss.dbeaver.ui.UIUtils;

import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * GenericDataSource
 */
public class OracleDataSource extends JDBCDataSource
    implements DBSObjectSelector, DBCQueryPlanner, IAdaptable
{
    static final Log log = LogFactory.getLog(OracleDataSource.class);

    private final static Map<String, ClassLoader> ociClassLoadersCache = new HashMap<String, ClassLoader>();

    final SchemaCache schemaCache = new SchemaCache();
    final DataTypeCache dataTypeCache = new DataTypeCache();
    final TablespaceCache tablespaceCache = new TablespaceCache();
    final UserCache userCache = new UserCache();
    final ProfileCache profileCache = new ProfileCache();
    final RoleCache roleCache = new RoleCache();

    private OracleSchema publicSchema;
    private String activeSchemaName;
    private boolean isAdmin;
    private String planTableName;

    public OracleDataSource(DBSDataSourceContainer container)
        throws DBException
    {
        super(container);
    }

    protected String getConnectionUserName(DBPConnectionInfo connectionInfo)
    {
        final Object role = connectionInfo.getProperties().get(OracleConstants.PROP_INTERNAL_LOGON);
        return role == null ? connectionInfo.getUserName() : connectionInfo.getUserName() + " AS " + role;
    }

    protected DBPDataSourceInfo makeInfo(JDBCDatabaseMetaData metaData)
    {
        final JDBCDataSourceInfo info = new JDBCDataSourceInfo(this, metaData);
        for (String kw : OracleConstants.ADVANCED_KEYWORDS) {
            info.addSQLKeyword(kw);
        }
        return info;
    }

    protected Map<String, String> getInternalConnectionProperties()
    {
        return OracleDataSourceProvider.getConnectionsProps();
    }

    public boolean isAdmin()
    {
        return isAdmin;
    }

    @Association
    public Collection<OracleSchema> getSchemas(DBRProgressMonitor monitor) throws DBException
    {
        return schemaCache.getObjects(monitor, this);
    }

    public OracleSchema getSchema(DBRProgressMonitor monitor, String name) throws DBException
    {
        if (publicSchema != null && publicSchema.getName().equals(name)) {
            return publicSchema;
        }
        return schemaCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<OracleTablespace> getTablespaces(DBRProgressMonitor monitor) throws DBException
    {
        return tablespaceCache.getObjects(monitor, this);
    }

    @Association
    public Collection<OracleUser> getUsers(DBRProgressMonitor monitor) throws DBException
    {
        return userCache.getObjects(monitor, this);
    }

    @Association
    public Collection<OracleUserProfile> getProfiles(DBRProgressMonitor monitor) throws DBException
    {
        return profileCache.getObjects(monitor, this);
    }

    @Association
    public Collection<OracleRole> getRoles(DBRProgressMonitor monitor) throws DBException
    {
        return roleCache.getObjects(monitor, this);
    }

    @Association
    public Collection<OracleSynonym> getPublicSynonyms(DBRProgressMonitor monitor) throws DBException
    {
        return publicSchema.getSynonyms(monitor);
    }

    @Association
    public Collection<OracleDBLink> getPublicDatabaseLinks(DBRProgressMonitor monitor) throws DBException
    {
        return publicSchema.getDatabaseLinks(monitor);
    }

    @Association
    public Collection<OracleRecycledObject> getUserRecycledObjects(DBRProgressMonitor monitor) throws DBException
    {
        return publicSchema.getRecycledObjects(monitor);
    }

    @Override
    protected Driver getDriverInstance() throws DBException {
        DBSDataSourceContainer container = getContainer();
        DBPDriver driver = container.getDriver();
        boolean ociDriver = OCIUtils.isOciDriver(driver);
        if (ociDriver) {
            String homeId = container.getConnectionInfo().getClientHomeId();
            if (homeId != null) {
                ClassLoader ociClassLoader = ociClassLoadersCache.get(homeId);
                if (ociClassLoader == null) {
                    OracleHomeDescriptor homeDescriptor = (OracleHomeDescriptor)driver.getClientHome(homeId);
                    if (homeDescriptor == null) {
                        throw new DBException("Can't load driver from '" + homeId + "'");
                    }
                    ociClassLoader = new OCIClassLoader(homeDescriptor, getClass().getClassLoader());
                    ociClassLoadersCache.put(homeId, ociClassLoader);
                }
                String driverClassName = driver.getDriverClassName();
                try {
                    final Class<?> driverClass = ociClassLoader.loadClass(driverClassName);
                    return (Driver) driverClass.newInstance();
                } catch (ClassNotFoundException ex) {
                    throw new DBException("Can't load driver class '" + driverClassName + "'", ex);
                } catch (InstantiationException ex) {
                    throw new DBException("Can't create driver class '" + driverClassName + "'", ex);
                } catch (IllegalAccessException ex) {
                    throw new DBException("Can't create driver class '" + driverClassName + "'", ex);
                }
            }
        }
        return super.getDriverInstance();
    }

    public void initialize(DBRProgressMonitor monitor)
        throws DBException
    {
        super.initialize(monitor);

        this.publicSchema = new OracleSchema(this, 1, OracleConstants.USER_PUBLIC);
        {
            final JDBCExecutionContext context = openContext(monitor, DBCExecutionPurpose.META, "Load data source meta info");
            try {
                // Get user roles
                this.isAdmin = false;
                JDBCPreparedStatement dbStat = context.prepareStatement(
                    "SELECT GRANTED_ROLE FROM USER_ROLE_PRIVS");
                try {
                    JDBCResultSet resultSet = dbStat.executeQuery();
                    try {
                        while (resultSet.next()) {
                            final String role = resultSet.getString(1);
                            if (role.equals("DBA")) {
                                isAdmin = true;
                            }
                        }
                    } finally {
                        resultSet.close();
                    }
                } finally {
                    dbStat.close();
                }

                // Get active schema
                dbStat = context.prepareStatement(
                    "SELECT SYS_CONTEXT( 'USERENV', 'CURRENT_SCHEMA' ) FROM DUAL");
                try {
                    JDBCResultSet resultSet = dbStat.executeQuery();
                    try {
                        resultSet.next();
                        this.activeSchemaName = resultSet.getString(1);
                    } finally {
                        resultSet.close();
                    }
                } finally {
                    dbStat.close();
                }
            } catch (SQLException e) {
                log.error(e);
            }
            finally {
                context.close();
            }
        }
        this.dataTypeCache.getObjects(monitor, this);
    }

    public boolean refreshObject(DBRProgressMonitor monitor)
        throws DBException
    {
        super.refreshObject(monitor);

        this.schemaCache.clearCache();
        this.dataTypeCache.clearCache();
        this.tablespaceCache.clearCache();
        this.userCache.clearCache();
        this.profileCache.clearCache();
        this.roleCache.clearCache();
        this.activeSchemaName = null;

        this.initialize(monitor);

        return true;
    }

    public Collection<OracleSchema> getChildren(DBRProgressMonitor monitor)
        throws DBException
    {
        return getSchemas(monitor);
    }

    public OracleSchema getChild(DBRProgressMonitor monitor, String childName)
        throws DBException
    {
        return getSchema(monitor, childName);
    }

    public Class<? extends OracleSchema> getChildType(DBRProgressMonitor monitor)
        throws DBException
    {
        return OracleSchema.class;
    }

    public void cacheStructure(DBRProgressMonitor monitor, int scope)
        throws DBException
    {
        
    }

    public boolean supportsObjectSelect()
    {
        return true;
    }

    public OracleSchema getSelectedObject()
    {
        return schemaCache.getCachedObject(activeSchemaName);
    }

    public void selectObject(DBRProgressMonitor monitor, DBSObject object)
        throws DBException
    {
        final OracleSchema oldSelectedEntity = getSelectedObject();
        if (object == oldSelectedEntity) {
            return;
        }
        if (!(object instanceof OracleSchema)) {
            throw new IllegalArgumentException("Invalid object type: " + object);
        }
        JDBCExecutionContext context = openContext(monitor, DBCExecutionPurpose.META, "Set active schema");
        try {
            JDBCPreparedStatement dbStat = context.prepareStatement("ALTER SESSION SET CURRENT_SCHEMA=" + object.getName());
            try {
                dbStat.execute();
            } finally {
                dbStat.close();
            }
        } catch (SQLException e) {
            throw new DBException(e);
        }
        finally {
            context.close();
        }
        activeSchemaName = object.getName();

        // Send notifications
        if (oldSelectedEntity != null) {
            DBUtils.fireObjectSelect(oldSelectedEntity, false);
        }
        if (this.activeSchemaName != null) {
            DBUtils.fireObjectSelect(object, true);
        }
    }

    public DBCPlan planQueryExecution(DBCExecutionContext context, String query) throws DBCException
    {
        OraclePlanAnalyser plan = new OraclePlanAnalyser(this, query);
        plan.explain((JDBCExecutionContext) context);
        return plan;
    }

    public Object getAdapter(Class adapter)
    {
        if (adapter == DBSStructureAssistant.class) {
            return new OracleStructureAssistant(this);
        }
        return null;
    }

    public OracleDataSource getDataSource() {
        return this;
    }

    public Collection<? extends DBSDataType> getDataTypes()
    {
        return dataTypeCache.getCachedObjects();
    }

    public DBSDataType getDataType(String typeName)
    {
        return dataTypeCache.getCachedObject(typeName);
    }

    public String getPlanTableName(JDBCExecutionContext context)
        throws SQLException
    {
        if (planTableName == null) {
            String[] candidateNames = new String[] {"PLAN_TABLE", "TOAD_PLAN_TABLE"};
            for (String candidate : candidateNames) {
                try {
                    JDBCUtils.executeSQL(context, "SELECT 1 FROM " + candidate);
                } catch (SQLException e) {
                    // No such table
                    continue;
                }
                planTableName = candidate;
                break;
            }
            if (planTableName == null) {
                // Plan table not found - try to create new one
                if (!UIUtils.confirmAction(
                    DBeaverCore.getActiveWorkbenchShell(),
                    "Oracle PLAN_TABLE missing",
                    "PLAN_TABLE not found in current user's context. " +
                        "Do you want DBeaver to create new PLAN_TABLE?"))
                {
                    return null;
                }
                planTableName = createPlanTable(context);
            }
        }
        return planTableName;
    }

    private String createPlanTable(JDBCExecutionContext context) throws SQLException
    {
        JDBCUtils.executeSQL(context, OracleConstants.PLAN_TABLE_DEFINITION);
        return "PLAN_TABLE";
    }

    static class SchemaCache extends JDBCObjectCache<OracleDataSource, OracleSchema> {
        @Override
        protected JDBCStatement prepareObjectsStatement(JDBCExecutionContext context, OracleDataSource owner) throws SQLException
        {
            List<String> schemaFilters = SQLUtils.splitFilter(owner.getContainer().getSchemaFilter());
            StringBuilder schemasQuery = new StringBuilder();
            boolean manyUsers = false;
            if (manyUsers) {
                schemasQuery.append("SELECT U.USERNAME FROM SYS.ALL_USERS U\n" +
                    "WHERE U.USERNAME IN (SELECT DISTINCT OWNER FROM SYS.ALL_OBJECTS)\n");
            } else {
                schemasQuery.append("SELECT U.USERNAME FROM SYS.ALL_USERS U\n");

                if (owner.isAdmin() && false) {
                    schemasQuery.append(
                        "WHERE (U.USER_ID IN (SELECT DISTINCT OWNER# FROM SYS.OBJ$) ");
                } else {
                    schemasQuery.append(
                        "WHERE (U.USERNAME IN (SELECT DISTINCT OWNER FROM SYS.ALL_OBJECTS)");
                }
            }

            if (!schemaFilters.isEmpty()) {
                schemasQuery.append(" AND (");
                for (int i = 0; i < schemaFilters.size(); i++) {
                    if (i > 0) {
                        schemasQuery.append(" OR ");
                    }
                    schemasQuery.append("USERNAME LIKE ?");
                }
                schemasQuery.append(")");
            }
            schemasQuery.append(")");
            //if (!CommonUtils.isEmpty(owner.activeSchemaName)) {
                //schemasQuery.append("\nUNION ALL SELECT '").append(owner.activeSchemaName).append("' AS USERNAME FROM DUAL");
            //}
            //schemasQuery.append("\nORDER BY USERNAME");

            JDBCPreparedStatement dbStat = context.prepareStatement(schemasQuery.toString());

            if (!schemaFilters.isEmpty()) {
                for (int i = 0; i < schemaFilters.size(); i++) {
                    dbStat.setString(i + 1, schemaFilters.get(i));
                }
            }
            return dbStat;
        }

        @Override
        protected OracleSchema fetchObject(JDBCExecutionContext context, OracleDataSource owner, ResultSet resultSet) throws SQLException, DBException
        {
            return new OracleSchema(owner, resultSet);
        }

        @Override
        protected void invalidateObjects(DBRProgressMonitor monitor, OracleDataSource owner, Iterator<OracleSchema> objectIter)
        {
            setListOrderComparator(DBUtils.<OracleSchema>nameComparator());
            // Add predefined types
            if (getCachedObject(owner.activeSchemaName) == null) {
                cacheObject(
                    new OracleSchema(owner, -1, owner.activeSchemaName));
            }
        }
    }

    static class DataTypeCache extends JDBCObjectCache<OracleDataSource, OracleDataType> {
        @Override
        protected JDBCStatement prepareObjectsStatement(JDBCExecutionContext context, OracleDataSource owner) throws SQLException
        {
            return context.prepareStatement(
                "SELECT * FROM SYS.ALL_TYPES WHERE OWNER IS NULL ORDER BY TYPE_NAME");
        }
        @Override
        protected OracleDataType fetchObject(JDBCExecutionContext context, OracleDataSource owner, ResultSet resultSet) throws SQLException, DBException
        {
            return new OracleDataType(owner, resultSet);
        }

        @Override
        protected void invalidateObjects(DBRProgressMonitor monitor, OracleDataSource owner, Iterator<OracleDataType> objectIter)
        {
            // Add predefined types
            for (Map.Entry<String, OracleDataType.TypeDesc> predefinedType : OracleDataType.PREDEFINED_TYPES.entrySet()) {
                if (getCachedObject(predefinedType.getKey()) == null) {
                    cacheObject(
                        new OracleDataType(owner, predefinedType.getKey(), true));
                }
            }
        }
    }

    static class TablespaceCache extends JDBCObjectCache<OracleDataSource, OracleTablespace> {
        @Override
        protected JDBCStatement prepareObjectsStatement(JDBCExecutionContext context, OracleDataSource owner) throws SQLException
        {
            return context.prepareStatement(
                "SELECT * FROM " + OracleUtils.getAdminViewPrefix(owner) + "TABLESPACES ORDER BY TABLESPACE_NAME");
        }

        @Override
        protected OracleTablespace fetchObject(JDBCExecutionContext context, OracleDataSource owner, ResultSet resultSet) throws SQLException, DBException
        {
            return new OracleTablespace(owner, resultSet);
        }
    }

    static class UserCache extends JDBCObjectCache<OracleDataSource, OracleUser> {
        @Override
        protected JDBCStatement prepareObjectsStatement(JDBCExecutionContext context, OracleDataSource owner) throws SQLException
        {
            return context.prepareStatement(
                "SELECT * FROM " + OracleUtils.getAdminAllViewPrefix(owner) + "USERS ORDER BY USERNAME");
        }

        @Override
        protected OracleUser fetchObject(JDBCExecutionContext context, OracleDataSource owner, ResultSet resultSet) throws SQLException, DBException
        {
            return new OracleUser(owner, resultSet);
        }
    }

    static class RoleCache extends JDBCObjectCache<OracleDataSource, OracleRole> {
        @Override
        protected JDBCStatement prepareObjectsStatement(JDBCExecutionContext context, OracleDataSource owner) throws SQLException
        {
            return context.prepareStatement(
                "SELECT * FROM DBA_ROLES ORDER BY ROLE");
        }

        @Override
        protected OracleRole fetchObject(JDBCExecutionContext context, OracleDataSource owner, ResultSet resultSet) throws SQLException, DBException
        {
            return new OracleRole(owner, resultSet);
        }
    }

    static class ProfileCache extends JDBCStructCache<OracleDataSource, OracleUserProfile, OracleUserProfile.ProfileResource> {
        protected ProfileCache()
        {
            super("PROFILE");
        }

        @Override
        protected JDBCStatement prepareObjectsStatement(JDBCExecutionContext context, OracleDataSource owner) throws SQLException
        {
            return context.prepareStatement(
                "SELECT DISTINCT PROFILE FROM DBA_PROFILES ORDER BY PROFILE");
        }

        @Override
        protected OracleUserProfile fetchObject(JDBCExecutionContext context, OracleDataSource owner, ResultSet resultSet) throws SQLException, DBException
        {
            return new OracleUserProfile(owner, resultSet);
        }

        @Override
        protected boolean isChildrenCached(OracleUserProfile parent)
        {
            return parent.isResourcesCached();
        }

        @Override
        protected void cacheChildren(OracleUserProfile parent, List<OracleUserProfile.ProfileResource> resources)
        {
            parent.setResources(resources);
        }

        @Override
        protected JDBCStatement prepareChildrenStatement(JDBCExecutionContext context, OracleDataSource dataSource, OracleUserProfile forObject) throws SQLException
        {
            final JDBCPreparedStatement dbStat = context.prepareStatement(
                "SELECT RESOURCE_NAME,RESOURCE_TYPE,LIMIT FROM DBA_PROFILES " +
                (forObject == null ? "" : "WHERE PROFILE=? ") +
                "ORDER BY RESOURCE_NAME");
            if (forObject != null) {
                dbStat.setString(1, forObject.getName());
            }
            return dbStat;
        }

        @Override
        protected OracleUserProfile.ProfileResource fetchChild(JDBCExecutionContext context, OracleDataSource dataSource, OracleUserProfile parent, ResultSet dbResult) throws SQLException, DBException
        {
            return new OracleUserProfile.ProfileResource(parent, dbResult);
        }
    }

}
