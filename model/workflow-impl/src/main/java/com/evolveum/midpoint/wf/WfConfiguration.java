/*
 * Copyright (c) 2012 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */
package com.evolveum.midpoint.wf;

import com.evolveum.midpoint.common.configuration.api.MidpointConfiguration;
import com.evolveum.midpoint.init.RepositoryFactory;
import com.evolveum.midpoint.repo.api.RepositoryServiceFactoryException;
import com.evolveum.midpoint.repo.sql.SqlRepositoryConfiguration;
import com.evolveum.midpoint.repo.sql.SqlRepositoryFactory;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.processors.primary.PrimaryApprovalProcessWrapper;
import com.evolveum.midpoint.wf.processors.ChangeProcessor;
import com.evolveum.midpoint.wf.processors.primary.PrimaryUserChangeProcessor;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Pavol Mederly
 */
@Component
@DependsOn({ "midpointConfiguration" })

public class WfConfiguration implements BeanFactoryAware {

    private static final transient Trace LOGGER = TraceManager.getTrace(WfConfiguration.class);
    private static final String DEFAULT_CHANGE_PROCESSOR_PACKAGE = PrimaryUserChangeProcessor.class.getPackage().getName();

    @Autowired(required = true)
    private MidpointConfiguration midpointConfiguration;

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    private static final String WF_CONFIG_SECTION = "midpoint.workflow";
    private static final String AUTO_DEPLOYMENT_FROM_DEFAULT = "classpath*:processes/*.bpmn20.xml";

    private Boolean enabled = null;

    private boolean activitiSchemaUpdate;

    private String jdbcDriver;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;

    private String[] changeProcessors;

    private int processCheckInterval;
    private String autoDeploymentFrom;

    @PostConstruct
    void initialize() {

        Configuration c = midpointConfiguration.getConfiguration(WF_CONFIG_SECTION);

        enabled = c.getBoolean("enabled", true);
        if (!enabled) {
            return;
        }

        // activiti properties related to database connection will be taken from SQL repository
        SqlRepositoryConfiguration sqlConfig = null;
        String defaultJdbcUrlPrefix = null;
        try {
            RepositoryFactory repositoryFactory = (RepositoryFactory) beanFactory.getBean("repositoryFactory");
            if (!(repositoryFactory.getFactory() instanceof SqlRepositoryFactory)) {    // it may be null as well
                LOGGER.debug("SQL configuration cannot be found; Activiti database configuration (if any) will be taken from 'workflow' configuration section only");
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("repositoryFactory.getFactory() = " + repositoryFactory);
                }
            } else {
                SqlRepositoryFactory sqlRepositoryFactory = (SqlRepositoryFactory) repositoryFactory.getFactory();
                sqlConfig = sqlRepositoryFactory.getSqlConfiguration();
                if (sqlConfig.isEmbedded()) {
                    defaultJdbcUrlPrefix = sqlRepositoryFactory.prepareJdbcUrlPrefix(sqlConfig);
                }
            }
        } catch(NoSuchBeanDefinitionException e) {
            LOGGER.debug("SqlRepositoryFactory is not available, Activiti database configuration (if any) will be taken from 'workflow' configuration section only.");
            LOGGER.trace("Reason is", e);
        } catch (RepositoryServiceFactoryException e) {
            LoggingUtils.logException(LOGGER, "Cannot determine default JDBC URL for embedded database", e);
        }

        jdbcUrl = c.getString("jdbcUrl", null);
        if (jdbcUrl == null) {
            if (sqlConfig.isEmbedded()) {
                jdbcUrl = defaultJdbcUrlPrefix + "-activiti;DB_CLOSE_ON_EXIT=FALSE";
            } else {
                jdbcUrl = sqlConfig.getJdbcUrl();
            }
        }
        LOGGER.info("Activiti database is at " + jdbcUrl);

        activitiSchemaUpdate = c.getBoolean("activitiSchemaUpdate", true);
        jdbcDriver = c.getString("jdbcDriver", sqlConfig != null ? sqlConfig.getDriverClassName() : null);
        jdbcUser = c.getString("jdbcUser", sqlConfig != null ? sqlConfig.getJdbcUsername() : null);
        jdbcPassword = c.getString("jdbcPassword", sqlConfig != null ? sqlConfig.getJdbcPassword() : null);

        processCheckInterval = c.getInt("processCheckInterval", 10);    // todo set to bigger default for production use
        autoDeploymentFrom = c.getString("autoDeploymentFrom", AUTO_DEPLOYMENT_FROM_DEFAULT);

        changeProcessors = c.getStringArray("changeProcessor");

//        hibernateDialect = sqlConfig != null ? sqlConfig.getHibernateDialect() : "";

        validate();
    }

    void validate() {

        notEmpty(jdbcDriver, "JDBC driver must be specified (either explicitly or in SQL repository configuration)");
        notEmpty(jdbcUrl, "JDBC URL must be specified (either explicitly or in SQL repository configuration).");
        notNull(jdbcUser, "JDBC user name must be specified (either explicitly or in SQL repository configuration).");
        notNull(jdbcPassword, "JDBC password must be specified (either explicitly or in SQL repository configuration).");
    }

    private void notEmpty(String value, String message) {
        if (StringUtils.isEmpty(value)) {
            throw new SystemException(message);
        }
    }

    private void notNull(String value, String message) {
        if (value == null) {
            throw new SystemException(message);
        }
    }

    public boolean isActivitiSchemaUpdate() {
        return activitiSchemaUpdate;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public String getJdbcDriver() {
        return jdbcDriver;
    }

    public String getJdbcPassword() {
        return jdbcPassword;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getJdbcUser() {
        return jdbcUser;
    }

    public int getProcessCheckInterval() {
        return processCheckInterval;
    }

    public String getAutoDeploymentFrom() {
        return autoDeploymentFrom;
    }

    public List<ChangeProcessor> getChangeProcessorsBeans() {
        List<ChangeProcessor> retval = new ArrayList<ChangeProcessor>();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Resolving change processors: configured list = " + StringUtils.join(changeProcessors, ","));
        }
        if (changeProcessors != null) {
            for (String processorName : changeProcessors) {
                LOGGER.trace("Searching for change processor " + processorName);
                try {
                    ChangeProcessor processor = (ChangeProcessor) beanFactory.getBean(processorName);
                    retval.add(processor);
                } catch(BeansException e) {
                    throw new SystemException("Change processor " + processorName + " could not be found.", e);
                }
            }
        }
        LOGGER.debug("Resolved " + retval.size() + " change processors.");
        return retval;
    }

    public List<PrimaryApprovalProcessWrapper> getPrimaryChangeProcessorWrappers(String beanName) {

        List<PrimaryApprovalProcessWrapper> retval = new ArrayList<PrimaryApprovalProcessWrapper>();

        Validate.notNull(midpointConfiguration, "midpointConfiguration was not initialized correctly (check spring beans initialization order)");

        if (changeProcessors == null || !Arrays.asList(changeProcessors).contains(beanName)) {
            LOGGER.info("Skipping reading configuration of " + beanName + ", as it is not on the list of change processors.");
            return retval;
        }

        Configuration c = midpointConfiguration.getConfiguration(WF_CONFIG_SECTION).subset(beanName);
        if (c.isEmpty()) {
            throw new SystemException("There is no configuration for primary change processor " + beanName);    // todo should be ConfigurationException perhaps
        }

        String[] wrappers = c.getStringArray("wrapper");
        if (wrappers == null || wrappers.length == 0) {
            LOGGER.warn("No wrappers defined for primary change processor " + beanName);
        } else {
            for (String wrapperName : wrappers) {
                LOGGER.trace("Searching for wrapper " + wrapperName);
                try {
                    PrimaryApprovalProcessWrapper wrapper = (PrimaryApprovalProcessWrapper) beanFactory.getBean(wrapperName);
                    retval.add(wrapper);
                } catch(BeansException e) {
                    throw new SystemException("Process wrapper " + wrapperName + " could not be found.", e);
                }
            }
            LOGGER.debug("Resolved " + retval.size() + " process wrappers for primary change processor " + beanName);
        }
        return retval;
    }
}
