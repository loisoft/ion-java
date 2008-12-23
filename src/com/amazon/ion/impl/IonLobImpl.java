/*
 * Copyright (c) 2007-2008 Amazon.com, Inc.  All rights reserved.
 */

package com.amazon.ion.impl;

import com.amazon.ion.IonException;
import com.amazon.ion.IonLob;
import com.amazon.ion.NullValueException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The abstract parent of all Ion lob types.
 */
public abstract class IonLobImpl
    extends IonValueImpl
    implements IonLob
{

    private byte[] _lob_value;

    protected IonLobImpl(IonSystemImpl system, int typeDesc)
    {
        super(system, typeDesc);
    }


    @Override
    public abstract IonLobImpl clone();


    /**
     * this copies the contents of the lob from the source to
     * this instance (or the "null-ness" if the source is null).
     * It delegates up to IonValueImpl to copy the annotations
     * and field name as necessary.
     *
     * @param source instance to copy from; must not be null.
     * Will be materialized as a side-effect.
     */
    protected final void copyFrom(IonLobImpl source)
    {
        copyAnnotationsFrom(source); // materializes this and the source
        setBytes(source._lob_value);
    }

    /**
     * @param source may be null to make this an Ion null value.
     */
    protected final void copyBytesFrom(byte[] source, int offset, int length)
    {
        if (source == null)
        {
            _lob_value = null;
        }
        else
        {
            // Avoid allocation if we happen to have the right length.
            if (_lob_value == null || _lob_value.length != length) {
                _lob_value = new byte[length];
            }
            System.arraycopy(source, offset, _lob_value, 0, length);
        }
        _hasNativeValue = true;
        setDirty();
    }


    public final InputStream newInputStream()
    {
        if (isNullValue()) return null;

        makeReady();
        // TODO this is inefficient.  Should stream directly from binary.
        return new ByteArrayInputStream(_lob_value);
    }

    public final byte[] newBytes()
    {
        makeReady();
        return (_lob_value == null ? null : _lob_value.clone());
    }

    public final void setBytes(byte[] bytes)
    {
        setBytes(bytes, 0, bytes == null ? 0 : bytes.length);
    }

    public final void setBytes(byte[] bytes, int offset, int length)
    {
        checkForLock();
        copyBytesFrom(bytes, offset, length);
    }


    public final int byteSize()
    {
        makeReady();
        if (_lob_value == null) throw new NullValueException();
        return _lob_value.length;
    }


    @Override
    protected final int getNativeValueLength()
    {
        assert _hasNativeValue == true;
        if (_lob_value == null) return 0;
        return _lob_value.length;
    }


    @Override
    protected final int computeLowNibble(int valuelen)
    {
        assert _hasNativeValue == true;

        int ln = 0;
        if (_lob_value == null) {
            ln = IonConstants.lnIsNullAtom;
        }
        else {
            ln = getNativeValueLength();
            if (ln > IonConstants.lnIsVarLen) {
                ln = IonConstants.lnIsVarLen;
            }
        }
        return ln;
    }


    @Override
    protected final void doMaterializeValue(IonBinary.Reader reader)
        throws IOException
    {
        assert this._isPositionLoaded == true && this._buffer != null;

        // a native value trumps a buffered value
        if (_hasNativeValue) return;

        // the reader will have been positioned for us
        assert reader.position() == this.pos_getOffsetAtValueTD();

        // we need to skip over the td to get to the good stuff
        int td = reader.read();
        assert (byte)(0xff & td) == this.pos_getTypeDescriptorByte();

        int type = this.pos_getType();
        if (type != IonConstants.tidClob && type != IonConstants.tidBlob) {
            throw new IonException("invalid type desc encountered for value");
        }

        int ln = this.pos_getLowNibble();
        switch ((0xf & ln)) {
        case IonConstants.lnIsNullAtom:
            _lob_value = null;
            break;
        case 0:
            _lob_value = new byte[0];
            break;
        case IonConstants.lnIsVarLen:
            ln = reader.readVarUInt7IntValue();
            // fall through to default:
        default:
            _lob_value = new byte[ln];
            reader.read(_lob_value, 0, ln);
            break;
        }

        _hasNativeValue = true;
    }

    @Override
    protected final void doWriteNakedValue(IonBinary.Writer writer,
                                           int valueLen)
        throws IOException
    {
        assert valueLen == this.getNakedValueLength();
        assert valueLen > 0;

        writer.write(_lob_value, 0, valueLen);

        return;
    }

    @Override
    public final synchronized boolean isNullValue()
    {
        if (!_hasNativeValue) return super.isNullValue();
        return (_lob_value == null);
    }
}
