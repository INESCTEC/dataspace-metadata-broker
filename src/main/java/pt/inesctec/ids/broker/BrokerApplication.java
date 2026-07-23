/* Copyright (C) [2023-2026], INESC TEC
 *
 * dataspace-metadata-broker
 * Implements a central repository registry and discovery service for data providers and consumers within an International Data Spaces (IDS) ecosystem.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed 
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
 * CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations 
 * under the License.
 *
 * You can reach INESC TEC Technology Transfer Office (TTO) at tech-transfer@inesctec.pt, or
 * Campus da Faculdade de Engenharia da Universidade do Porto
 * Rua Dr. Roberto Frias
 * 4200-465 Porto
 * Portugal 
 */
package pt.inesctec.ids.broker;

import de.fraunhofer.iais.eis.ids.component.core.*;
import de.fraunhofer.iais.eis.ids.component.interaction.validation.ShaclValidator;
import ids.messaging.core.config.ConfigContainer;
import ids.messaging.core.daps.DapsTokenProvider;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import pt.inesctec.ids.broker.dto.BrokerSelfDescription;
import pt.inesctec.ids.broker.service.AdminGraphService;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.ExceptionUtil;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;

import java.io.IOException;

@Log4j2
@SpringBootApplication
@ComponentScan({
				"pt.inesctec.ids.broker",
				"ids.*",
				"ids.messaging.*"
})
@Configuration
public class BrokerApplication implements CommandLineRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger(BrokerApplication.class);

	@Autowired
	AdminGraphService adminGraphService;

	@Autowired private FusekiConnectionAccessor accessor;


	@Value("${shacl.validation}")
	private boolean shacl;

	/**
	 * The current connector configuration.
	 */
	@Autowired
	private @NonNull ConfigContainer configContainer;


	public static void main(String[] args) {
		try {
			SpringApplication application = new SpringApplication(BrokerApplication.class);
			//application.setWebApplicationType(WebApplicationType.NONE);
			application.run(args);
		}
		catch (Throwable e) {
			// https://stackoverflow.com/questions/32770884/breakpoint-at-throw-new-silentexitexception-in-eclipse-spring-boot
			if (e.getClass().getName().contains("SilentExitException"))
				LOGGER.debug("Spring is restarting the main thread - See spring-boot-devtools");
			else
				LOGGER.error("Application crashed! : {}", ExceptionUtil.getFullStackTrace(e));
		}
	}

	@Override
	public void run(String... args) throws Exception {
		LOGGER.info("Broker started with the arguments: broker.catalog.uri: "+accessor.getCatalogUri() + "" +
				", fuseki.server:" + accessor.getFusekiServer()+
				", fusekiEndpoint: "+ accessor.getFusekiEndpoint());

		adminGraphService.initializeAdminGraph();

		//ModelService modelService=new ModelService(accessor);
		//modelService.removeAllData();
		//modelService.removeConnectorGraph("http://localwslhost:8082/connectors/postmanREST/2145279871");

		if(shacl) {
			try {
				ShaclValidator.initialize();
			} catch (IOException e) {
				LOGGER.warn("Failed to initialize Shapes for SHACL validation.", e);
			}
		}
	}

	/**
	 * This function generates a default self-description
	 * @return Self-description of this component
	 */
	@Bean
	public BrokerSelfDescription createSelfDescriptionProvider()  {
		return new BrokerSelfDescription(configContainer, accessor.getCatalogUri());
	}


}
