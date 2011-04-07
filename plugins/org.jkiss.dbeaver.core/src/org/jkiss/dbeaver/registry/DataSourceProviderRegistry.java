/*
 * Copyright (c) 2011, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.registry;

import net.sf.jkiss.utils.xml.SAXReader;
import net.sf.jkiss.utils.xml.XMLBuilder;
import net.sf.jkiss.utils.xml.XMLException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDriver;
import org.jkiss.dbeaver.model.DBPRegistryListener;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;
import org.jkiss.dbeaver.runtime.RuntimeUtils;
import org.jkiss.dbeaver.utils.ContentUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DataSourceProviderRegistry
{
    static final Log log = LogFactory.getLog(DataSourceProviderRegistry.class);

    private final List<DataSourceProviderDescriptor> dataSourceProviders = new ArrayList<DataSourceProviderDescriptor>();
    private final List<DataTypeProviderDescriptor> dataTypeProviders = new ArrayList<DataTypeProviderDescriptor>();
    private final List<DBPRegistryListener> registryListeners = new ArrayList<DBPRegistryListener>();

    public DataSourceProviderRegistry()
    {
    }

    public void loadExtensions(IExtensionRegistry registry)
    {
        // Load datasource providers from external plugins
        {
            IConfigurationElement[] extElements = registry.getConfigurationElementsFor(DataSourceProviderDescriptor.EXTENSION_ID);
            for (IConfigurationElement ext : extElements) {
                DataSourceProviderDescriptor provider = new DataSourceProviderDescriptor(this, ext);
                dataSourceProviders.add(provider);
            }
            Collections.sort(dataSourceProviders, new Comparator<DataSourceProviderDescriptor>() {
                public int compare(DataSourceProviderDescriptor o1, DataSourceProviderDescriptor o2)
                {
                    if (o1.isDriversManagable() && !o2.isDriversManagable()) {
                        return 1;
                    }
                    if (o2.isDriversManagable() && !o1.isDriversManagable()) {
                        return -1;
                    }
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            });
        }

        // Load data type providers from external plugins
        {
            IConfigurationElement[] extElements = registry.getConfigurationElementsFor(DataTypeProviderDescriptor.EXTENSION_ID);
            for (IConfigurationElement ext : extElements) {
                DataTypeProviderDescriptor provider = new DataTypeProviderDescriptor(this, ext);
                dataTypeProviders.add(provider);
            }
        }

        // Load drivers
        File driversConfig = new File(DBeaverCore.getInstance().getRootPath().toFile(), DataSourceConstants.DRIVERS_FILE_NAME);
        if (!driversConfig.exists()) {
            driversConfig = new File(RuntimeUtils.getBetaDir(), DataSourceConstants.DRIVERS_FILE_NAME);
            if (driversConfig.exists()) {
                loadDrivers(driversConfig);
                saveDrivers();
            }
        } else {
            loadDrivers(driversConfig);
        }
    }

    public void dispose()
    {
        synchronized (registryListeners) {
            if (!registryListeners.isEmpty()) {
                log.warn("Some datasource registry listeners are still registered: " + registryListeners);
            }
            registryListeners.clear();
        }
        for (DataSourceProviderDescriptor providerDescriptor : this.dataSourceProviders) {
            providerDescriptor.dispose();
        }
        this.dataSourceProviders.clear();

        this.dataTypeProviders.clear();
    }

    public DataSourceProviderDescriptor getDataSourceProvider(String id)
    {
        for (DataSourceProviderDescriptor provider : dataSourceProviders) {
            if (provider.getId().equals(id)) {
                return provider;
            }
        }
        return null;
    }

    public List<DataSourceProviderDescriptor> getDataSourceProviders()
    {
        return dataSourceProviders;
    }

    ////////////////////////////////////////////////////
    // DataType providers

    public List<DataTypeProviderDescriptor> getDataTypeProviders()
    {
        return dataTypeProviders;
    }

    public DataTypeProviderDescriptor getDataTypeProvider(DBPDataSource dataSource, DBSTypedObject type)
    {
        DBPDriver driver = dataSource.getContainer().getDriver();
        if (!(driver instanceof DriverDescriptor)) {
            log.warn("Bad datasource specified (driver is not recognized by registry) - " + dataSource);
            return null;
        }
        DataSourceProviderDescriptor dsProvider = ((DriverDescriptor) driver).getProviderDescriptor();

        // First try to find type provider for specific datasource type
        for (DataTypeProviderDescriptor dtProvider : dataTypeProviders) {
            if (!dtProvider.isDefault() && dtProvider.supportsDataSource(dsProvider) && dtProvider.supportsType(type)) {
                return dtProvider;
            }
        }

        // Find in default providers
        for (DataTypeProviderDescriptor dtProvider : dataTypeProviders) {
            if (dtProvider.isDefault() && dtProvider.supportsType(type)) {
                return dtProvider;
            }
        }
        return null;
    }

    public static DataSourceProviderRegistry getDefault()
    {
        return DBeaverCore.getInstance().getDataSourceProviderRegistry();
    }

    private void loadDrivers(File driversConfig)
    {
        if (driversConfig.exists()) {
            try {
                InputStream is = new FileInputStream(driversConfig);
                try {
                    try {
                        loadDrivers(is);
                    } catch (DBException ex) {
                        log.warn("Can't load drivers config from " + driversConfig.getPath(), ex);
                    }
                    finally {
                        is.close();
                    }
                }
                catch (IOException ex) {
                    log.warn("IO error", ex);
                }
            } catch (FileNotFoundException ex) {
                log.warn("Can't open config file " + driversConfig.getPath(), ex);
            }
        }
    }

    private void loadDrivers(InputStream is)
        throws DBException, IOException
    {
        SAXReader parser = new SAXReader(is);
        try {
            parser.parse(new DriverDescriptor.DriversParser());
        }
        catch (XMLException ex) {
            throw new DBException("Datasource config parse error", ex);
        }
    }

    public void saveDrivers()
    {
        File driversConfig = new File(DBeaverCore.getInstance().getRootPath().toFile(), DataSourceConstants.DRIVERS_FILE_NAME);
        try {
            OutputStream os = new FileOutputStream(driversConfig);
            try {
                XMLBuilder xml = new XMLBuilder(os, ContentUtils.DEFAULT_FILE_CHARSET);
                xml.setButify(true);
                xml.startElement(DataSourceConstants.TAG_DRIVERS);
                for (DataSourceProviderDescriptor provider : this.dataSourceProviders) {
                    xml.startElement(DataSourceConstants.TAG_PROVIDER);
                    xml.addAttribute(DataSourceConstants.ATTR_ID, provider.getId());
                    for (DriverDescriptor driver : provider.getDrivers()) {
                        if (driver.isModified()) {
                            driver.serialize(xml, false);
                        }
                    }
                    xml.endElement();
                }
                xml.endElement();
                xml.flush();
                os.close();
            }
            catch (IOException ex) {
                log.warn("IO error", ex);
            }
        } catch (FileNotFoundException ex) {
            log.warn("Can't open config file " + driversConfig.getPath(), ex);
        }
    }

    public void addDataSourceRegistryListener(DBPRegistryListener listener)
    {
        synchronized (registryListeners) {
            registryListeners.add(listener);
        }
    }

    public void removeDataSourceRegistryListener(DBPRegistryListener listener)
    {
        synchronized (registryListeners) {
            registryListeners.remove(listener);
        }
    }

    void fireRegistryChange(DataSourceRegistry registry, boolean load)
    {
        synchronized (registryListeners) {
            for (DBPRegistryListener listener : registryListeners) {
                if (load) {
                    listener.handleRegistryLoad(registry);
                } else {
                    listener.handleRegistryUnload(registry);
                }
            }
        }
    }
}
