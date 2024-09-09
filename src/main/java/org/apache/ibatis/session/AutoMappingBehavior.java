/*
 *    Copyright 2009-2022 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.session;

/**
 * Specifies if and how MyBatis should automatically map columns to fields/properties.
 * 指定 MyBatis 是否以及如何自动将列映射到字段/属性。
 *
 * @author Eduardo Macarron
 */
public enum AutoMappingBehavior {

    /**
     * Disables auto-mapping.
     * 禁用自动映射。
     */
    NONE,

    /**
     * Will only auto-map results with no nested result mappings defined inside.
     * 将仅自动映射结果，内部未定义嵌套结果映射。
     */
    PARTIAL,

    /**
     * Will auto-map result mappings of any complexity (containing nested or otherwise).
     * 将自动映射任何复杂度的结果映射（包含嵌套或其他）。
     */
    FULL
}
