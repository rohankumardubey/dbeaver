package org.jkiss.dbeaver.runtime.load;

import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

import java.lang.reflect.InvocationTargetException;

/**
 * Lazy loading service
 * @param <RESULT> result type
 */
public interface ILoadService<RESULT> {

    String getServiceName();

    DBRProgressMonitor getProgressMonitor();

    void setProgressMonitor(DBRProgressMonitor monitor);

    void setNestedService(ILoadService nested);

    void clearNestedService();

    RESULT evaluate() throws InvocationTargetException, InterruptedException;

    boolean cancel();

}
