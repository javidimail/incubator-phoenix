/*
 * Copyright 2014 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.expression;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.List;

import org.apache.hadoop.hbase.io.ImmutableBytesWritable;

import org.apache.phoenix.query.QueryConstants;
import org.apache.phoenix.schema.ColumnModifier;
import org.apache.phoenix.schema.PDataType;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.util.DateUtil;

/**
 * 
 * Class to encapsulate addition arithmetic for {@link PDataType#TIMESTAMP}.
 *
 * 
 * @since 2.1.3
 */

public class TimestampAddExpression extends AddExpression {

    public TimestampAddExpression() {
    }

    public TimestampAddExpression(List<Expression> children) {
        super(children);
    }

    @Override
    public boolean evaluate(Tuple tuple, ImmutableBytesWritable ptr) {
        BigDecimal finalResult = BigDecimal.ZERO;
        
        for(int i=0; i<children.size(); i++) {
            if (!children.get(i).evaluate(tuple, ptr)) {
                return false;
            }
            if (ptr.getLength() == 0) {
                return true;
            }
            BigDecimal value;
            PDataType type = children.get(i).getDataType();
            ColumnModifier columnModifier = children.get(i).getColumnModifier();
            if(type == PDataType.TIMESTAMP || type == PDataType.UNSIGNED_TIMESTAMP) {
                value = (BigDecimal)(PDataType.DECIMAL.toObject(ptr, type, columnModifier));
            } else if (type.isCoercibleTo(PDataType.DECIMAL)) {
                value = (((BigDecimal)PDataType.DECIMAL.toObject(ptr, columnModifier)).multiply(QueryConstants.BD_MILLIS_IN_DAY)).setScale(6, RoundingMode.HALF_UP);
            } else if (type.isCoercibleTo(PDataType.DOUBLE)) {
                value = ((BigDecimal.valueOf(type.getCodec().decodeDouble(ptr, columnModifier))).multiply(QueryConstants.BD_MILLIS_IN_DAY)).setScale(6, RoundingMode.HALF_UP);
            } else {
                value = BigDecimal.valueOf(type.getCodec().decodeLong(ptr, columnModifier));
            } 
            finalResult = finalResult.add(value);
        }
        Timestamp ts = DateUtil.getTimestamp(finalResult);
        byte[] resultPtr = new byte[getDataType().getByteSize()];
        PDataType.TIMESTAMP.toBytes(ts, resultPtr, 0);
        ptr.set(resultPtr);
        return true;
    }

    @Override
    public final PDataType getDataType() {
        return PDataType.TIMESTAMP;
    }

}
