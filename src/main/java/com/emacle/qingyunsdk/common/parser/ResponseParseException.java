/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.emacle.qingyunsdk.common.parser;

/**
 * The exception from parsing service response.
 */
public class ResponseParseException extends Exception {
    private static final long serialVersionUID = -6660159156997037589L;

    public ResponseParseException() {
        super();
    }
    
    public ResponseParseException(String message) {
        super(message);
    }
    
    public ResponseParseException(Throwable cause) {
        super(cause);
    }
    
    public ResponseParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
