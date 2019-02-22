/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.siddhi.core.executor.math.add;

import io.siddhi.core.event.ComplexEvent;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import io.siddhi.query.api.definition.Attribute;

/**
 * Executor class for Integer Add function. Function execution logic is implemented in execute here.
 */
public class AddExpressionExecutorInt implements ExpressionExecutor {
    private ExpressionExecutor leftExpressionExecutor;
    private ExpressionExecutor rightExpressionExecutor;

    public AddExpressionExecutorInt(ExpressionExecutor leftExpressionExecutor,
                                    ExpressionExecutor rightExpressionExecutor) {
        this.leftExpressionExecutor = leftExpressionExecutor;
        this.rightExpressionExecutor = rightExpressionExecutor;
    }

    @Override
    public Object execute(ComplexEvent event) {
        Object leftObject = leftExpressionExecutor.execute(event);
        Object rightObject = rightExpressionExecutor.execute(event);
        if (leftObject == null || rightObject == null) {
            return null;
        }
        return ((Number) leftObject).intValue() + ((Number) rightObject).intValue();

    }

    public Attribute.Type getReturnType() {
        return Attribute.Type.INT;
    }

    @Override
    public ExpressionExecutor cloneExecutor(String key) {
        return new AddExpressionExecutorInt(leftExpressionExecutor.cloneExecutor(key), rightExpressionExecutor
                .cloneExecutor(key));
    }

    @Override
    public void clean() {
        leftExpressionExecutor.clean();
        rightExpressionExecutor.clean();
    }


}
