/*
 * Copyright (c) 2010-2017 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.model.impl.sync;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.ModelPublicConstants;
import com.evolveum.midpoint.model.api.context.EvaluatedPolicyRule;
import com.evolveum.midpoint.model.api.context.EvaluatedPolicyRuleTrigger;
import com.evolveum.midpoint.model.impl.lens.Clockwork;
import com.evolveum.midpoint.model.impl.lens.ContextFactory;
import com.evolveum.midpoint.model.impl.lens.EvaluatedPolicyRuleImpl;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.lens.LensFocusContext;
import com.evolveum.midpoint.model.impl.util.AbstractScannerResultHandler;
import com.evolveum.midpoint.model.impl.util.AbstractScannerTaskHandler;
import com.evolveum.midpoint.notifications.api.NotificationManager;
import com.evolveum.midpoint.notifications.api.events.CustomEvent;
import com.evolveum.midpoint.notifications.api.events.ModelEvent;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.schema.result.OperationConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskRunResult;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.NotificationPolicyActionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PartialProcessingOptionsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PartialProcessingTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyConstraintKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyRuleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TimeValidityPolicyConstraintType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.ItemPathType;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType.F_VALID_FROM;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType.F_VALID_TO;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType.F_ACTIVATION;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType.F_ASSIGNMENT;

/**
 * 
 * @author Radovan Semancik
 *
 */
@Component
public class FocusValidityScannerTaskHandler extends AbstractScannerTaskHandler<UserType, AbstractScannerResultHandler<UserType>> {

	// WARNING! This task handler is efficiently singleton!
	// It is a spring bean and it is supposed to handle all search task instances
	// Therefore it must not have task-specific fields. It can only contain fields specific to
	// all tasks of a specified type
	
	public static final String HANDLER_URI = ModelPublicConstants.FOCUS_VALIDITY_SCANNER_TASK_HANDLER_URI;

	@Autowired private ContextFactory contextFactory;
    @Autowired private Clockwork clockwork;
    // task OID -> object OIDs; cleared on task start
	// we use plain map, as it is much easier to synchronize explicitly than to play with ConcurrentMap methods
	private Map<String,Set<String>> processedOidsMap = new HashMap<>();

	private synchronized void initProcessedOids(Task coordinatorTask) {
		Validate.notNull(coordinatorTask.getOid(), "Task OID is null");
		processedOidsMap.put(coordinatorTask.getOid(), new HashSet<String>());
	}

	// TODO fix possible (although very small) memory leak occurring when task finishes unsuccessfully
	private synchronized void cleanupProcessedOids(Task coordinatorTask) {
		Validate.notNull(coordinatorTask.getOid(), "Task OID is null");
		processedOidsMap.remove(coordinatorTask.getOid());
	}

	private synchronized boolean oidAlreadySeen(Task coordinatorTask, String objectOid) {
		Validate.notNull(coordinatorTask.getOid(), "Coordinator task OID is null");
		Set<String> oids = processedOidsMap.get(coordinatorTask.getOid());
		if (oids == null) {
			throw new IllegalStateException("ProcessedOids set was not initialized for task = " + coordinatorTask);
		}
		return !oids.add(objectOid);
	}

	private static final transient Trace LOGGER = TraceManager.getTrace(FocusValidityScannerTaskHandler.class);

	public FocusValidityScannerTaskHandler() {
        super(UserType.class, "Focus validity scan", OperationConstants.FOCUS_VALIDITY_SCAN);
    }

	@PostConstruct
	private void initialize() {
		taskManager.registerHandler(HANDLER_URI, this);
	}

	@Override
	protected Class<? extends ObjectType> getType(Task task) {
		Class<? extends ObjectType> type = getTypeFromTask(task, UserType.class);
		if (type == null) {
			return UserType.class;
		}
		return type;
		
	}

	@Override
	protected ObjectQuery createQuery(AbstractScannerResultHandler<UserType> handler, TaskRunResult runResult, Task coordinatorTask, OperationResult opResult) throws SchemaException {
		initProcessedOids(coordinatorTask);
	
		TimeValidityPolicyConstraintType validtyContraintType = getValidityPolicyConstraint(coordinatorTask);
		Duration activateOn = null;
		if (validtyContraintType != null) {
			activateOn = validtyContraintType.getActivateOn();
		}
		
		ObjectQuery query = new ObjectQuery();
		ObjectFilter filter;
//		PrismObjectDefinition<UserType> focusObjectDef = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(UserType.class);
		
		XMLGregorianCalendar lastScanTimestamp = handler.getLastScanTimestamp();
		XMLGregorianCalendar thisScanTimestamp = handler.getThisScanTimestamp();
		if (activateOn != null) {
			ItemPathType itemPathType = validtyContraintType.getItem();
			ItemPath path = itemPathType.getItemPath();
			if (path == null) {
				throw new SchemaException("No path defined in the validity constraint.");
			}
			thisScanTimestamp.add(activateOn.negate());
			filter = createFilterFor(path, lastScanTimestamp, thisScanTimestamp);
			
		} else {
		
			filter = createBasicFilter(lastScanTimestamp, thisScanTimestamp);
		}
		
		query.setFilter(filter);
		return query;
	}
	
	private ObjectFilter createBasicFilter(XMLGregorianCalendar lastScanTimestamp, XMLGregorianCalendar thisScanTimestamp){
		if (lastScanTimestamp == null) {
			return QueryBuilder.queryFor(FocusType.class, prismContext)
					.item(F_ACTIVATION, F_VALID_FROM).le(thisScanTimestamp)
					.or().item(F_ACTIVATION, F_VALID_TO).le(thisScanTimestamp)
					.or().exists(F_ASSIGNMENT)
						.block()
							.item(AssignmentType.F_ACTIVATION, F_VALID_FROM).le(thisScanTimestamp)
							.or().item(AssignmentType.F_ACTIVATION, F_VALID_TO).le(thisScanTimestamp)
						.endBlock()
					.buildFilter();
		}
		
		return QueryBuilder.queryFor(FocusType.class, prismContext)
					.item(F_ACTIVATION, F_VALID_FROM).gt(lastScanTimestamp)
						.and().item(F_ACTIVATION, F_VALID_FROM).le(thisScanTimestamp)
					.or().item(F_ACTIVATION, F_VALID_TO).gt(lastScanTimestamp)
						.and().item(F_ACTIVATION, F_VALID_TO).le(thisScanTimestamp)
					.or().exists(F_ASSIGNMENT)
						.block()
							.item(AssignmentType.F_ACTIVATION, F_VALID_FROM).gt(lastScanTimestamp)
								.and().item(AssignmentType.F_ACTIVATION, F_VALID_FROM).le(thisScanTimestamp)
							.or().item(AssignmentType.F_ACTIVATION, F_VALID_TO).gt(lastScanTimestamp)
								.and().item(AssignmentType.F_ACTIVATION, F_VALID_TO).le(thisScanTimestamp)
						.endBlock()
					.buildFilter();
		
	}
	
	private ObjectFilter createFilterFor(ItemPath path, XMLGregorianCalendar lastScanTimestamp, XMLGregorianCalendar thisScanTimestamp){
		if (lastScanTimestamp == null) {
			return QueryBuilder.queryFor(FocusType.class, prismContext)
					.item(path).le(thisScanTimestamp)
					.buildFilter();
		}
		
		return QueryBuilder.queryFor(FocusType.class, prismContext)
					.item(path).gt(lastScanTimestamp)
						.and().item(path).le(thisScanTimestamp)
					.buildFilter();
		
	}

	@Override
	protected void finish(AbstractScannerResultHandler<UserType> handler, TaskRunResult runResult, Task coordinatorTask, OperationResult opResult)
			throws SchemaException {
		super.finish(handler, runResult, coordinatorTask, opResult);
		cleanupProcessedOids(coordinatorTask);
	}

	@Override
	protected AbstractScannerResultHandler<UserType> createHandler(TaskRunResult runResult, final Task coordinatorTask,
			OperationResult opResult) {
		
		AbstractScannerResultHandler<UserType> handler = new AbstractScannerResultHandler<UserType>(
				coordinatorTask, FocusValidityScannerTaskHandler.class.getName(), "recompute", "recompute task", taskManager) {
			@Override
			protected boolean handleObject(PrismObject<UserType> object, Task workerTask, OperationResult result) throws CommonException {
				if (oidAlreadySeen(coordinatorTask, object.getOid())) {
					LOGGER.trace("Recomputation already executed for {}", ObjectTypeUtil.toShortString(object));
				} else {
					reconcileUser(object, workerTask, result);
				}
				return true;
			}
		};
        handler.setStopOnError(false);
		return handler;
	}

	private void reconcileUser(PrismObject<UserType> user, Task workerTask, OperationResult result) throws SchemaException,
			ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ObjectAlreadyExistsException, 
			ConfigurationException, PolicyViolationException, SecurityViolationException {
		LOGGER.trace("Recomputing user {}", user);
		// We want reconcile option here. There may be accounts that are in wrong activation state. 
		// We will not notice that unless we go with reconcile.
		LensContext<UserType> lensContext = contextFactory.createRecomputeContext(user, ModelExecuteOptions.createReconcile(), workerTask, result);
		if (isNotifyAction(workerTask)) {
			EvaluatedPolicyRule policyRule = new EvaluatedPolicyRuleImpl(workerTask.getPolicyRule(), null);
			EvaluatedPolicyRuleTrigger<TimeValidityPolicyConstraintType> evaluatedTrigger = new EvaluatedPolicyRuleTrigger<TimeValidityPolicyConstraintType>(PolicyConstraintKindType.TIME_VALIDITY, getValidityPolicyConstraint(workerTask), "Applying time validity constraint for focus");
			policyRule.getTriggers().add(evaluatedTrigger);
			lensContext.getFocusContext().addPolicyRule(policyRule);
		}
		LOGGER.trace("Recomputing of user {}: context:\n{}", user, lensContext.debugDump());
		clockwork.run(lensContext, workerTask, result);
		LOGGER.trace("Recomputing of user {}: {}", user, result.getStatus());
	}
	
	private TimeValidityPolicyConstraintType getValidityPolicyConstraint(Task coordinatorTask) {
		PolicyRuleType policyRule = coordinatorTask.getPolicyRule();
		
		if (policyRule == null) {
			return null;
		}
		
		if (policyRule.getPolicyConstraints() == null) {
			return null;
		}
		
		List<TimeValidityPolicyConstraintType> timeValidityContstraints = policyRule.getPolicyConstraints().getTimeValidity();
		if (CollectionUtils.isEmpty(timeValidityContstraints)){
			return null;
		}
		
		return timeValidityContstraints.iterator().next();
		
	}
	
	private NotificationPolicyActionType getAction(Task coordinatorTask){
		PolicyRuleType policyRule = coordinatorTask.getPolicyRule();
		
		if (policyRule == null) {
			return null;
		}
		
		if (policyRule.getPolicyActions() == null) {
			return null;
		}
		
		return policyRule.getPolicyActions().getNotification();
	}
	
	private boolean isNotifyAction(Task coordinatorTask) {
		return getAction(coordinatorTask) != null;
	}
	
	private boolean isTimeValidityConstraint(Task coordinatorTask){
		return getValidityPolicyConstraint(coordinatorTask) != null;
	}
	
}
