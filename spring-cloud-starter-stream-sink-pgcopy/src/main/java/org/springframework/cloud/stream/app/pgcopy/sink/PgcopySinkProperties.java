/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.app.pgcopy.sink;

import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Used to configure the pgcopy sink module options that are related to writing using the PostgreSQL CopyManager API.
 *
 * @author Thomas Risberg
 */
@SuppressWarnings("unused")
@ConfigurationProperties("pgcopy")
public class PgcopySinkProperties {

	/**
	 * The name of the table to write into.
	 */
	@NotNull
	private String tableName;

	/**
	 * Threshold in number of messages when file will be automatically flushed and rolled over.
	 */
	private int batchSize = 10000;

	/**
	 * Idle timeout in milliseconds when Hadoop file resource is automatically closed.
	 */
	private long idleTimeout = -1L;

	public Character getDelimiter() {
		return delimiter;
	}

	public void setDelimiter(Character delimiter) {
		this.delimiter = delimiter;
	}

	private Character delimiter = ',';

	public Boolean getIgnoreFirst() {
		return ignoreFirst;
	}

	public void setIgnoreFirst(Boolean ignoreFirst) {
		this.ignoreFirst = ignoreFirst;
	}

	private Boolean ignoreFirst = false;

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public long getIdleTimeout() {
		return idleTimeout;
	}

	public void setIdleTimeout(long idleTimeout) {
		this.idleTimeout = idleTimeout;
	}
}