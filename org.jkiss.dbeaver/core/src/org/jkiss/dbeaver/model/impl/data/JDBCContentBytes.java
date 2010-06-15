/*
 * Copyright (c) 2010, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.model.impl.data;

import net.sf.jkiss.utils.streams.MimeTypes;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.data.DBDContentBinary;
import org.jkiss.dbeaver.model.data.DBDValueController;
import org.jkiss.dbeaver.model.dbc.DBCException;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;
import org.jkiss.dbeaver.runtime.sql.ISQLQueryListener;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * JDBCBLOB
 *
 * @author Serge Rider
 */
public class JDBCContentBytes extends JDBCContentAbstract implements DBDContentBinary {

    static Log log = LogFactory.getLog(JDBCContentBytes.class);

    private byte[] data;

    public JDBCContentBytes(byte[] data) {
        this.data = data;
    }

    public long getContentLength() {
        if (data == null) {
            return 0;
        }
        return data.length;
    }

    public String getContentType()
    {
        return MimeTypes.OCTET_STREAM;
    }

    public InputStream getContents() throws DBCException {
        if (data == null) {
            // Empty content
            return new ByteArrayInputStream(new byte[0]);
        } else {
            return new ByteArrayInputStream(data);
        }
    }

    public void updateContents(
        DBDValueController valueController,
        InputStream stream,
        long contentLength,
        DBRProgressMonitor monitor,
        ISQLQueryListener listener)
        throws DBException
    {
        if (stream == null) {
            data = null;
        } else {
            data = new byte[(int) contentLength];
            try {
                int count = stream.read(data);
                if (count != contentLength) {
                    log.warn("Actual content length (" + count + ") is less than declared (" + contentLength + ")");
                }
            }
            catch (IOException e) {
                throw new DBCException("IO error", e);
            }
        }
        valueController.updateValueImmediately(this, listener);
    }

    public void bindParameter(PreparedStatement preparedStatement, DBSTypedObject columnType, int paramIndex)
        throws DBCException
    {
        try {
            if (data != null) {
                preparedStatement.setBytes(paramIndex, data);
            } else {
                preparedStatement.setNull(paramIndex, columnType.getValueType());
            }
        }
        catch (SQLException e) {
            throw new DBCException("JDBC error", e);
        }
    }

    public boolean isNull()
    {
        return data == null;
    }

    public JDBCContentBytes makeNull()
    {
        return new JDBCContentBytes(null);
    }

    @Override
    public String toString() {
        if (data == null) {
            return null;
        }
        return "binary [" + data.length + "]";
    }

}