package ca.uhn.fhir.jpa.subscription.channel.subscription;

/*-
 * #%L
 * HAPI FHIR Subscription Server
 * %%
 * Copyright (C) 2014 - 2020 University Health Network
 * %%
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
 * #L%
 */

import ca.uhn.fhir.jpa.subscription.model.ResourceModifiedMessage;
import ca.uhn.fhir.jpa.subscription.channel.queue.IQueueChannelFactory;
import ca.uhn.fhir.jpa.subscription.model.ResourceDeliveryMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.SubscribableChannel;

public class SubscriptionChannelFactory {

	@Autowired
	private IQueueChannelFactory mySubscribableChannelFactory;

	public SubscribableChannel newDeliveryChannel(String theChannelName) {
		return mySubscribableChannelFactory.getOrCreateReceiver(theChannelName, ResourceDeliveryMessage.class, mySubscribableChannelFactory.getDeliveryChannelConcurrentConsumers());
	}

	public SubscribableChannel newMatchingSendingChannel(String theChannelName) {
		return mySubscribableChannelFactory.getOrCreateReceiver(theChannelName, ResourceModifiedMessage.class, mySubscribableChannelFactory.getMatchingChannelConcurrentConsumers());
	}

	public SubscribableChannel newMatchingReceivingChannel(String theChannelName) {
		return mySubscribableChannelFactory.getOrCreateReceiver(theChannelName, ResourceModifiedMessage.class, mySubscribableChannelFactory.getMatchingChannelConcurrentConsumers());
	}

}
