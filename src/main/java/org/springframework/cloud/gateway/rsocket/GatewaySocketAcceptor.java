/*
 * Copyright 2018-2019 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.rsocket;

import java.util.Collections;
import java.util.List;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

import org.springframework.util.CollectionUtils;

public class GatewaySocketAcceptor implements SocketAcceptor {

	private final Registry registry;
	private final RSocket proxyRSocket;

	public GatewaySocketAcceptor(Registry registry, RSocket proxyRSocket) {
		this.registry = registry;
		this.proxyRSocket = proxyRSocket;
	}

	@Override
	public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {

		if (setup.hasMetadata()) { // and setup.metadataMimeType() is Announcement metadata
			String annoucementMetadata = Metadata.decodeAnnouncement(setup.sliceMetadata());
			List<String> tags = Collections.singletonList(annoucementMetadata);
			registry.register(tags, sendingSocket);

			List<MonoProcessor<RSocket>> processors = this.registry.getPendingRequests(tags);
			if (!CollectionUtils.isEmpty(processors)) {
				processors.forEach(processor -> {
					processor.log("resume-pending-request");
					processor.onNext(sendingSocket);
					processor.onComplete();
				});
			}
		}

		return Mono.just(proxyRSocket);
	}
}
