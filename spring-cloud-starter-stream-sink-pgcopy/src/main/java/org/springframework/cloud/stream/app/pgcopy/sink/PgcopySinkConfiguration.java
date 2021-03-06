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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.binding.InputBindingLifecycle;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.integration.aggregator.DefaultAggregatingMessageGroupProcessor;
import org.springframework.integration.aggregator.ExpressionEvaluatingCorrelationStrategy;
import org.springframework.integration.aggregator.MessageCountReleaseStrategy;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.AggregatorFactoryBean;
import org.springframework.integration.store.MessageGroupStore;
import org.springframework.integration.store.MessageGroupStoreReaper;
import org.springframework.integration.store.SimpleMessageStore;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.nativejdbc.Jdbc4NativeJdbcExtractor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration class for the PostgreSQL CopyManager.
 *
 * @author Thomas Risberg
 */
@Configuration
@EnableScheduling
@EnableBinding(Sink.class)
@EnableConfigurationProperties(PgcopySinkProperties.class)
public class PgcopySinkConfiguration {

	private static final Log logger = LogFactory.getLog(PgcopySinkConfiguration.class);

	@Autowired
	private PgcopySinkProperties properties;

	@Bean
	public MessageChannel toSink() {
		return new DirectChannel();
	}

	@Bean
	@Primary
	@ServiceActivator(inputChannel= Sink.INPUT)
	FactoryBean<MessageHandler> aggregatorFactoryBean(MessageChannel toSink, MessageGroupStore messageGroupStore) {
		AggregatorFactoryBean aggregatorFactoryBean = new AggregatorFactoryBean();
		aggregatorFactoryBean.setCorrelationStrategy(
				new ExpressionEvaluatingCorrelationStrategy("payload.getClass().name"));
		aggregatorFactoryBean.setReleaseStrategy(new MessageCountReleaseStrategy(properties.getBatchSize()));
		aggregatorFactoryBean.setMessageStore(messageGroupStore);
		aggregatorFactoryBean.setProcessorBean(new DefaultAggregatingMessageGroupProcessor());
		aggregatorFactoryBean.setExpireGroupsUponCompletion(true);
		aggregatorFactoryBean.setSendPartialResultOnExpiry(true);
		aggregatorFactoryBean.setOutputChannel(toSink);
		return aggregatorFactoryBean;
	}

	@Bean
	@ServiceActivator(inputChannel = "toSink")
	public MessageHandler datasetSinkMessageHandler(final JdbcTemplate jdbcTemplate) {

		String delimStr = ",";
		// the copy command
		if (properties.getDelimiter() == '\t')
			delimStr = "E'\t'";
		else
			delimStr = properties.getDelimiter().toString();

		final String sql = "COPY " + properties.getTableName() + " FROM STDIN WITH DELIMITER " + delimStr + " CSV";

		return new MessageHandler() {

			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				Object payload = message.getPayload();
				if (payload instanceof Collection<?>) {
					final Collection<?> payloads = (Collection<?>) payload;
					if (properties.getIgnoreFirst() && payloads.size()>1) {
						payloads.iterator().remove();
					}
					logger.debug("Executing batch of size " + payloads.size() + " for " + sql);
					try {
						jdbcTemplate.execute(
								new ConnectionCallback<Object>() {
									@Override
									public Object doInConnection(Connection connection) throws SQLException, DataAccessException {
										CopyManager cm = new CopyManager((BaseConnection) connection);
										CopyIn ci = cm.copyIn(sql);
										for (Object payloadData : (Collection<?>) payloads) {
											byte[] data = (payloadData+"\n").getBytes();
											ci.writeToCopy(data, 0, data.length);
										}
										long rows = ci.endCopy();
										logger.debug("Wrote " + rows + " rows");
										return Long.valueOf(rows);
									}
								}
						);
					} catch (DataAccessException e) {
						logger.error("Error while copying data: " + e.getMessage());
					}
				}
				else {
					throw new IllegalStateException("Expected a collection of strings but received " +
							message.getPayload().getClass().getName());
				}
			}
		};
	}

	@Bean
	MessageGroupStore messageGroupStore() {
		SimpleMessageStore messageGroupStore = new SimpleMessageStore();
		messageGroupStore.setTimeoutOnIdle(true);
		messageGroupStore.setCopyOnGet(false);
		return messageGroupStore;
	}

	@Bean
	MessageGroupStoreReaper messageGroupStoreReaper(MessageGroupStore messageStore,
													InputBindingLifecycle inputBindingLifecycle) {
		MessageGroupStoreReaper messageGroupStoreReaper = new MessageGroupStoreReaper(messageStore);
		messageGroupStoreReaper.setPhase(inputBindingLifecycle.getPhase() - 1);
		messageGroupStoreReaper.setTimeout(properties.getIdleTimeout());
		messageGroupStoreReaper.setAutoStartup(true);
		messageGroupStoreReaper.setExpireOnDestroy(true);
		return messageGroupStoreReaper;
	}

	@Bean
	ReaperTask reaperTask() {
		return new ReaperTask();
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		JdbcTemplate jt = new JdbcTemplate(dataSource);
		jt.setNativeJdbcExtractor(new Jdbc4NativeJdbcExtractor());
		return jt;
	}


	public static class ReaperTask {

		@Autowired
		MessageGroupStoreReaper messageGroupStoreReaper;

		@Scheduled(fixedRate=1000)
		public void reap() {
			messageGroupStoreReaper.run();
		}

		@PreDestroy
		public void beforeDestroy() {
			reap();
		}

	}
}