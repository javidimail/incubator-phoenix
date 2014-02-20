/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.apache.phoenix.expression;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.io.WritableUtils;
import org.apache.phoenix.schema.PArrayDataType;
import org.apache.phoenix.schema.PDataType;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.util.TrustedByteArrayOutputStream;

/**
 * Creates an expression for Upsert with Values/Select using ARRAY
 */
public class ArrayConstructorExpression extends BaseCompoundExpression {
    private PDataType baseType;
    private int position = -1;
    private Object[] elements;
    private TrustedByteArrayOutputStream byteStream = null;
    private DataOutputStream oStream = null;
    private int estimatedSize = 0;
    // store the offset postion in this.  Later based on the total size move this to a byte[]
    // and serialize into byte stream
    private int[] offsetPos;

    public ArrayConstructorExpression(List<Expression> children, PDataType baseType) {
        super(children);
        init(baseType);
        estimatedSize = PArrayDataType.estimateSize(this.children.size(), this.baseType);
        if (!this.baseType.isFixedWidth()) {
            offsetPos = new int[estimatedSize];
        }
    }

    private void init(PDataType baseType) {
        this.baseType = baseType;
        elements = new Object[getChildren().size()];
    }

    @Override
    public PDataType getDataType() {
        return PDataType.fromTypeId(baseType.getSqlType() + Types.ARRAY);
    }

    @Override
    public void reset() {
        super.reset();
        position = 0;
        Arrays.fill(elements, null);
    }

    @Override
    public boolean evaluate(Tuple tuple, ImmutableBytesWritable ptr) {
        try {
            int offset = 0;
            // track the elementlength for variable array
            int noOfElements =  children.size();
            int elementLength = 0;
            byteStream = new TrustedByteArrayOutputStream(estimatedSize);
            oStream = new DataOutputStream(byteStream);
            for (int i = position >= 0 ? position : 0; i < elements.length; i++) {
                Expression child = children.get(i);
                if (!child.evaluate(tuple, ptr)) {
                    if (tuple != null && !tuple.isImmutable()) {
                        if (position >= 0) position = i;
                        return false;
                    }
                } else {
                    // track the offset position here from the size of the byteStream
                    if (!baseType.isFixedWidth()) {
                        offset = byteStream.size();
                        offsetPos[i] = offset;
                        elementLength += ptr.getLength();
                    }
                    oStream.write(ptr.get(), ptr.getOffset(), ptr.getLength());
                }
            }
            if (position >= 0) position = elements.length;
            if (!baseType.isFixedWidth()) {
                noOfElements = PArrayDataType.serailizeOffsetArrayIntoStream(oStream, byteStream, noOfElements,
                        elementLength, offsetPos);
            }
            PArrayDataType.serializeHeaderInfoIntoStream(oStream, noOfElements);
            ptr.set(byteStream.getBuffer(), 0, byteStream.size());
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Exception while serializing the byte array");
        } finally {
            try {
                byteStream.close();
                oStream.close();
            } catch (IOException e) {
                // Should not happen
            }
        }
    }


    @Override
    public void readFields(DataInput input) throws IOException {
        super.readFields(input);
        int baseTypeOrdinal = WritableUtils.readVInt(input);
        init(PDataType.values()[baseTypeOrdinal]);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        super.write(output);
        WritableUtils.writeVInt(output, baseType.ordinal());
    }

}