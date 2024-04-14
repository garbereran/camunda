/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.engine.cdi;

import java.io.Serializable;
import java.util.Map;

import javax.enterprise.context.Conversation;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.cdi.annotation.BusinessProcessScoped;
import org.camunda.bpm.engine.cdi.impl.context.ContextAssociationManager;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.impl.VariableMapImpl;
import org.camunda.bpm.engine.variable.value.TypedValue;

/**
 * Bean supporting contextual business process management. This allows us to
 * implement a unit of work, in which a particular CDI scope (Conversation /
 * Request / Thread) is associated with a particular Execution / ProcessInstance
 * or Task.
 * <p />
 * The protocol is that we <em>associate</em> the {@link BusinessProcess} bean
 * with a particular Execution / Task, then perform some changes (retrieve / set process
 * variables) and then end the unit of work. This bean makes sure that our changes are
 * only "flushed" to the process engine when we successfully complete the unit of work.
 * <p />
 * A typical usage scenario might look like this:<br />
 * <strong>1st unit of work ("process instantiation"):</strong>
 * <pre>
 * conversation.begin();
 * ...
 * businessProcess.setVariable("billingId", "1"); // setting variables before starting the process
 * businessProcess.startProcessByKey("billingProcess");
 * conversation.end();
 * </pre>
 * <strong>2nd unit of work ("perform a user task"):</strong>
 * <pre>
 * conversation.begin();
 * businessProcess.startTask(id); // now we have associated a task with the current conversation
 * ...                            // this allows us to retrieve and change process variables
 *                                // and @BusinessProcessScoped beans
 * businessProcess.setVariable("billingDetails", "someValue"); // these changes are cached in the conversation
 * ...
 * businessProcess.completeTask(); // now all changed process variables are flushed
 * conversation.end();
 * </pre>
 * <p />
 * <strong>NOTE:</strong> in the absence of a conversation, (non faces request, i.e. when processing a JAX-RS,
 * JAX-WS, JMS, remote EJB or plain Servlet requests), the {@link BusinessProcess} bean associates with the
 * current Request (see {@link RequestScoped @RequestScoped}).
 * <p />
 * <strong>NOTE:</strong> in the absence of a request, ie. when the JobExecutor accesses
 * {@link BusinessProcessScoped @BusinessProcessScoped} beans, the execution is associated with the
 * current thread.
 *
 * @author Daniel Meyer
 * @author Falko Menge
 */
/**
 * provides a set of methods for managing the association between a process instance
 * and a task or execution, as well as flushing the cached variables to the Task or
 * Execution. The class also provides getters and setters for the associated execution
 * and task, and allows for the creation of a new process instance.
 */
@Named
public class BusinessProcess implements Serializable {

  private static final long serialVersionUID = 1L;

  @Inject private ProcessEngine processEngine;

  @Inject private ContextAssociationManager associationManager;

  @Inject private Instance<Conversation> conversationInstance;

  /**
   * starts a process instance by its definition ID, verifies that the command context
   * is not active, and sets the execution status to "ended" if necessary.
   * 
   * @param processDefinitionId identity of the process definition to start.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `instance`: The ProcessInstance object representing the started process instance.
   * 	- `isEnded()`: Indicates whether the process instance has ended or not. If the
   * process instance is ended, it means that the process has completed successfully
   * or has been terminated.
   * 	- `getAndClearCachedVariableMap()`: This method clears the cached variable map
   * for the process definition, which is necessary to avoid any potential issues with
   * caching and process instances.
   */
  public ProcessInstance startProcessById(String processDefinitionId) {
    assertCommandContextNotActive();

    ProcessInstance instance = processEngine.getRuntimeService().startProcessInstanceById(processDefinitionId, getAndClearCachedVariableMap());
    if (!instance.isEnded()) {
      setExecution(instance);
    }
    return instance;
  }

  /**
   * starts a process instance with the given process definition ID and business key,
   * using the process engine's `startProcessInstanceById` method. If the instance is
   * not already ended, it sets the execution of the instance to the calling context
   * and returns it.
   * 
   * @param processDefinitionId unique identifier of the process definition that is
   * being started.
   * 
   * @param businessKey business key of the process instance to be started, which is
   * used to identify the process instance in the process engine's repository.
   * 
   * @returns a `ProcessInstance` object representing the started process instance.
   * 
   * 	- `processInstance`: This is an instance of the process defined by the given
   * `processDefinitionId`, which has been started using the `startProcessInstanceById`
   * method.
   * 	- `isEnded()`: This indicates whether the process instance has ended or not. If
   * the process instance is ended, then it means that the process has completed
   * successfully and the instance is no longer active.
   * 	- `getAndClearCachedVariableMap()`: This is a method that clears the cache of
   * variables associated with the process instance, which ensures that the process
   * instance is always up-to-date and accurate.
   */
  public ProcessInstance startProcessById(String processDefinitionId, String businessKey) {
    assertCommandContextNotActive();

    ProcessInstance instance = processEngine.getRuntimeService().startProcessInstanceById(processDefinitionId, businessKey, getAndClearCachedVariableMap());
    if (!instance.isEnded()) {
      setExecution(instance);
    }
    return instance;
  }

  /**
   * starts a process instance with the given process definition ID and variables. It
   * retrieves the cached variables, puts them into the process instance, and sets the
   * execution state to indicate that it has started.
   * 
   * @param processDefinitionId ID of the process definition to start.
   * 
   * @param variables map of variables to be used when starting the process instance,
   * which is passed to the `getAndClearCachedVariableMap()` method and then added to
   * the process instance's variable map during its creation.
   * 
   * 	- `Map<String, Object> variables`: This is a map containing key-value pairs where
   * the keys are string identifiers and the values can be any type of data.
   * 	- `processDefinitionId`: The unique identifier of the process definition to start.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `instance`: This is an instance of the `ProcessInstance` class, which represents
   * a running process in the process engine. It contains information about the process
   * instance, such as its ID, name, and current state.
   * 	- `isEnded()`: This is a boolean value that indicates whether the process instance
   * has ended or not. If the process instance has ended, this method will return `true`,
   * otherwise it will return `false`.
   * 	- `setExecution()`: This is a method used to set the execution of the process
   * instance. It takes no arguments and is used to indicate that the process instance
   * has been started.
   */
  public ProcessInstance startProcessById(String processDefinitionId, Map<String, Object> variables) {
    assertCommandContextNotActive();

    VariableMap cachedVariables = getAndClearCachedVariableMap();
    cachedVariables.putAll(variables);
    ProcessInstance instance = processEngine.getRuntimeService().startProcessInstanceById(processDefinitionId, cachedVariables);
    if (!instance.isEnded()) {
      setExecution(instance);
    }
    return instance;
  }

  /**
   * starts a process instance with the given ID, business key, and variables. It
   * retrieves the process definition and starts the instance using the process engine's
   * `startProcessInstanceById` method, then sets the execution of the instance to the
   * function.
   * 
   * @param processDefinitionId id of the process definition to start.
   * 
   * @param businessKey unique identifier of the business process associated with the
   * given process definition ID, which is used to locate the appropriate process
   * instance in the process engine's runtime system.
   * 
   * @param variables map of variables that will be passed to the started process instance.
   * 
   * 	- `String businessKey`: The unique identifier of the business key for which the
   * process instance is being started.
   * 	- `Map<String, Object> variables`: A map containing the variable values passed
   * as input to the start process method.
   * 	- `ProcessEngine processEngine`: The reference to the ProcessEngine object that
   * is used to interact with the process engine.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `instance`: This is an instance of the `ProcessInstance` class, representing
   * the started process.
   * 	- `isEnded()`: A boolean value indicating whether the process has ended or not.
   * If the process has ended, this method will return `true`, otherwise it will return
   * `false`.
   */
  public ProcessInstance startProcessById(String processDefinitionId, String businessKey, Map<String, Object> variables) {
    assertCommandContextNotActive();

    VariableMap cachedVariables = getAndClearCachedVariableMap();
    cachedVariables.putAll(variables);
    ProcessInstance instance = processEngine.getRuntimeService().startProcessInstanceById(processDefinitionId, businessKey, cachedVariables);
    if (!instance.isEnded()) {
      setExecution(instance);
    }
    return instance;
  }

  /**
   * starts a process instance by its key and retrieves it from the engine if successful.
   * 
   * @param key unique process instance identifier to start.
   * 
   * @returns a `ProcessInstance` object representing the started process instance.
   * 
   * 	- `instance`: The ProcessInstance object that represents the started process instance.
   * 	- `isEnded`: A boolean indicating whether the process instance has been ended or
   * not. If true, the process instance is no longer active.
   * 	- `getAndClearCachedVariableMap()`: A method that clears the cached variable map
   * for the current command context before starting the process instance.
   */
  public ProcessInstance startProcessByKey(String key) {
    assertCommandContextNotActive();

    ProcessInstance instance = processEngine.getRuntimeService().startProcessInstanceByKey(key, getAndClearCachedVariableMap());
    if (!instance.isEnded()) {
      setExecution(instance);
    }
    return instance;
  }

  /**
   * starts a process instance by key, checks if it's ended, and sets execution context
   * if necessary.
   * 
   * @param key unique identifier of the process instance to start.
   * 
   * @param businessKey business key of the process instance to be started, which is
   * used to identify the specific process instance to be executed by the
   * `startProcessInstanceByKey()` method.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `instance`: The ProcessInstance object representing the started process instance.
   * 	- `isEnded()`: A boolean indicating whether the process instance has been ended
   * or not. If it is true, then the process instance has been ended, otherwise, it has
   * not.
   * 
   * Note that the output of the function is an instance of `ProcessInstance`, which
   * contains information about the started process instance, including its key, business
   * key, and other attributes.
   */
  public ProcessInstance startProcessByKey(String key, String businessKey) {
    assertCommandContextNotActive();

    ProcessInstance instance = processEngine.getRuntimeService().startProcessInstanceByKey(key, businessKey, getAndClearCachedVariableMap());
    if (!instance.isEnded()) {
      setExecution(instance);
    }
    return instance;
  }

  /**
   * starts a process instance based on its unique key and provides variables to the instance.
   * 
   * @param key unique process instance identifier for which to start the process.
   * 
   * @param variables map of variables that are passed to the process instance when it
   * is started, allowing for dynamic configuration of the instance's state.
   * 
   * 	- `Map<String, Object> variables`: This is an immutable map of strings to objects
   * that contains variable values to be passed to the process instance. The keys in
   * the map represent variable names, while the values represent the actual values of
   * those variables.
   * 	- `getAndClearCachedVariableMap()`: This function retrieves a cached variable map
   * and then clears it, ensuring that only the latest variable values are used for the
   * start process instance.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `instance`: The ProcessInstance object representing the started process instance.
   * 	- `isEnded()`: A boolean value indicating whether the process instance is ended
   * or not. If it is ended, further execution of the process instance is prohibited.
   */
  public ProcessInstance startProcessByKey(String key, Map<String, Object> variables) {
    assertCommandContextNotActive();

    VariableMap cachedVariables = getAndClearCachedVariableMap();
    cachedVariables.putAll(variables);
    ProcessInstance instance = processEngine.getRuntimeService().startProcessInstanceByKey(key, cachedVariables);
    if (!instance.isEnded()) {
      setExecution(instance);
    }
    return instance;
  }

  /**
   * starts a process instance based on its key and business key, passing variables to
   * it for customization. It retrieves cached variables, adds new ones, and passes
   * them to the process engine's `startProcessInstanceByKey` method to start the
   * instance. If the instance is not ended, it sets the execution status to the function.
   * 
   * @param key process instance key to be started.
   * 
   * @param businessKey business key of the process instance to be started, which is
   * used to identify the process instance and check its status before starting it.
   * 
   * @param variables map of variables that will be used to initialize the process
   * instance when it is started.
   * 
   * 	- `Map<String, Object> variables`: This is an unstructured map containing key-value
   * pairs where the keys are strings and the values can be any type, including primitive
   * types and objects.
   * 	- `getAndClearCachedVariableMap()`: This function retrieves the cached variable
   * map and then clears it to avoid storing unnecessary data.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `instance`: The ProcessInstance object represents the started process instance
   * in the process engine.
   * 	- `isEnded()`: This method returns whether the process instance is ended or not.
   * If the instance is not ended, then it means that the process is still ongoing.
   * 	- `setExecution()`: This method sets the execution of the ProcessInstance to the
   * specified instance.
   */
  public ProcessInstance startProcessByKey(String key, String businessKey, Map<String, Object> variables) {
    assertCommandContextNotActive();

    VariableMap cachedVariables = getAndClearCachedVariableMap();
    cachedVariables.putAll(variables);
    ProcessInstance instance = processEngine.getRuntimeService().startProcessInstanceByKey(key, businessKey, cachedVariables);
    if (!instance.isEnded()) {
      setExecution(instance);
    }
    return instance;
  }

  /**
   * starts a process instance by sending a message to the process engine, and sets the
   * execution of the instance if it is not ended already.
   * 
   * @param messageName name of the message that initiates the process instance start.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `ProcessInstance instance`: The ProcessInstance object representing the started
   * process.
   * 	- `isEnded()`: A boolean indicating whether the process is ended or not. If the
   * process is not ended, the function sets the execution of the ProcessInstance using
   * the `setExecution` method.
   */
  public ProcessInstance startProcessByMessage(String messageName) {
    assertCommandContextNotActive();

    VariableMap cachedVariables = getAndClearCachedVariableMap();
    ProcessInstance instance =  processEngine.getRuntimeService().startProcessInstanceByMessage(messageName, cachedVariables);
    if (!instance.isEnded()) {
      setExecution(instance);
    }
    return instance;
  }

  /**
   * starts a process instance by message name and passes variable map as argument. It
   * first checks if the command context is active, then gets and clears the cached
   * variable map, puts all process variables into it, starts the process instance using
   * the `getRuntimeService().startProcessInstanceByMessage` method, and sets the
   * execution of the instance.
   * 
   * @param messageName name of the message to start the process instance with.
   * 
   * @param processVariables variables that should be added to the existing variable
   * map of the process instance when starting it.
   * 
   * 	- `String messageName`: The name of the message that triggers the process instance.
   * 	- `Map<String, Object> processVariables`: A map containing variable assignments
   * for the process instance. These variables can be used to customize the behavior
   * of the process instance.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `ProcessInstance instance`: This is an instance of the `ProcessInstance` class,
   * representing the started process.
   * 	- `isEnded()`: This method returns a boolean indicating whether the process
   * instance has ended or not. If the process instance has ended, it means that the
   * process has completed successfully or failed.
   * 	- `setExecution()`: This method is used to set the execution of the process
   * instance. It sets the `ProcessInstance` object as the current execution, allowing
   * for further manipulation and querying of the process instance.
   */
  public ProcessInstance startProcessByMessage(String messageName, Map<String, Object> processVariables) {
    assertCommandContextNotActive();

    VariableMap cachedVariables = getAndClearCachedVariableMap();
    cachedVariables.putAll(processVariables);
    ProcessInstance instance =  processEngine.getRuntimeService().startProcessInstanceByMessage(messageName, cachedVariables);
    if (!instance.isEnded()) {
      setExecution(instance);
    }
    return instance;
  }

  /**
   * starts a process instance by message name and business key, using cached variables
   * to provide context for the process.
   * 
   * @param messageName name of the message to start the process instance with.
   * 
   * @param businessKey unique identifier of the business process being started.
   * 
   * @param processVariables map of variables that will be passed to the started process
   * instance, allowing it to access and use the required data.
   * 
   * 	- `String messageName`: The name of the message to start the process instance for.
   * 	- `String businessKey`: The unique identifier of the business key for the process
   * instance.
   * 	- `Map<String, Object> processVariables`: A map of variable names and values that
   * will be passed as arguments to the process instance.
   * 
   * @returns a `ProcessInstance` object representing the started process instance.
   * 
   * 	- `ProcessInstance`: The instance of the process that was started.
   * 	- `isEnded()`: A boolean value indicating whether the process instance is ended
   * or not. If the instance is not ended, the method sets the execution of the instance
   * using the `setExecution` method.
   */
  public ProcessInstance startProcessByMessage(String messageName, String businessKey, Map<String, Object> processVariables) {
    assertCommandContextNotActive();

    VariableMap cachedVariables = getAndClearCachedVariableMap();
    cachedVariables.putAll(processVariables);
    ProcessInstance instance =  processEngine.getRuntimeService().startProcessInstanceByMessage(messageName, businessKey, cachedVariables);
    if (!instance.isEnded()) {
      setExecution(instance);
    }
    return instance;
  }


  /**
   * Associate with the provided execution. This starts a unit of work.
   *
   * @param executionId
   *          the id of the execution to associate with.
   * @throw ProcessEngineCdiException
   *          if no such execution exists
   */
  /**
   * retrieves an Execution instance based on its ID and associates it with a variable
   * called `associationManager`.
   * 
   * @param executionId id of an execution for which the method will associate the given
   * execution with it.
   */
  public void associateExecutionById(String executionId) {
    Execution execution = processEngine.getRuntimeService()
      .createExecutionQuery()
      .executionId(executionId)
      .singleResult();
    if(execution == null) {
      throw new ProcessEngineCdiException("Cannot associate execution by id: no execution with id '"+executionId+"' found.");
    }
    associationManager.setExecution(execution);
  }

  /**
   * returns true if an {@link Execution} is associated.
   *
   * @see #associateExecutionById(String)
   */
  /**
   * checks if an execution ID is present in the association manager, indicating a
   * connection to another component.
   * 
   * @returns a boolean value indicating whether an execution ID is present.
   */
  public boolean isAssociated() {
    return associationManager.getExecutionId() != null;
  }

  /**
   * Signals the current execution, see {@link RuntimeService#signal(String)}
   * <p/>
   * Ends the current unit of work (flushes changes to process variables set
   * using {@link #setVariable(String, Object)} or made on
   * {@link BusinessProcessScoped @BusinessProcessScoped} beans).
   *
   * @throws ProcessEngineCdiException
   *           if no execution is currently associated
   * @throws ProcessEngineException
   *           if the activiti command fails
   */
  /**
   * is used to signal an execution in a process engine, associating and disassociating
   * variables with the execution and clearing cached variable maps.
   */
  public void signalExecution() {
    assertExecutionAssociated();
    processEngine.getRuntimeService().setVariablesLocal(associationManager.getExecutionId(), getAndClearCachedLocalVariableMap());
    processEngine.getRuntimeService().signal(associationManager.getExecutionId(), getAndClearCachedVariableMap());
    associationManager.disAssociate();
  }

  /**
   * @see #signalExecution()
   *
   * In addition, this method allows to end the current conversation
   */
  /**
   * executes a specific action or event and ends a conversation, depending on a provided
   * boolean parameter.
   * 
   * @param endConversation conclusion of a conversation and invokes the `end()` method
   * on the `conversationInstance` object when set to `true`.
   */
  public void signalExecution(boolean endConversation) {
    signalExecution();
    if(endConversation) {
      conversationInstance.get().end();
    }
  }

  // -------------------------------------

  /**
   * Associates the task with the provided taskId with the current conversation.
   * <p/>
   *
   * @param taskId
   *          the id of the task
   *
   * @return the resumed task
   *
   * @throws ProcessEngineCdiException
   *           if no such task is found
   */
  /**
   * retrieves a task by ID, creates it if not found, and associates its execution with
   * the given ID.
   * 
   * @param taskId ID of the task to be resumed, and is used to retrieve the corresponding
   * task object from the process engine's task service.
   * 
   * @returns a reference to the task with the specified ID, or an exception if the
   * task does not exist.
   * 
   * 	- `currentTask`: The current task being processed, which is retrieved from the `associationManager`.
   * 	- `taskId`: The ID of the task being started, which is passed as a parameter to
   * the function.
   * 	- `task`: The newly created task instance, which is returned if the task with the
   * provided ID does not exist or if the function successfully associates the task
   * with an execution.
   * 	- `executionId`: The ID of the execution associated with the task, which is
   * retrieved from the `associationManager`.
   * 
   * In summary, the `startTask` function retrieves the current task being processed,
   * checks if a task with the provided ID exists, and creates a new task instance if
   * necessary. It also associates the task with an execution and returns the task instance.
   */
  public Task startTask(String taskId) {
    Task currentTask = associationManager.getTask();
    if(currentTask != null && currentTask.getId().equals(taskId)) {
      return currentTask;
    }
    Task task = processEngine.getTaskService().createTaskQuery().taskId(taskId).singleResult();
    if(task == null) {
      throw new ProcessEngineCdiException("Cannot resume task with id '"+taskId+"', no such task.");
    }
    associationManager.setTask(task);
    associateExecutionById(task.getExecutionId());
    return task;
  }

  /**
   * @see #startTask(String)
   *
   * this method allows to start a conversation if no conversation is active
   */
  /**
   * initializes a task and initiates a conversation if required.
   * 
   * @param taskId identifier of the task to be started, which is passed as an argument
   * to the `startTask()` method for identification purposes.
   * 
   * @param beginConversation beginConversation method of a conversation object, which
   * determines whether to start a new conversation or continue an existing one.
   * 
   * @returns a Task object containing the result of starting the task.
   * 
   * 	- The input `taskId` is used to identify a specific task instance.
   * 	- The parameter `beginConversation` indicates whether a conversation should be
   * initiated for the task. If `true`, a new conversation instance is created and
   * began; otherwise, the existing conversation instance is used.
   * 	- The function call `startTask(taskId)` performs the actual task execution,
   * returning the task output.
   */
  public Task startTask(String taskId, boolean beginConversation) {
    if(beginConversation) {
      Conversation conversation = conversationInstance.get();
      if(conversation.isTransient()) {
       conversation.begin();
      }
    }
    return startTask(taskId);
  }

  /**
   * Completes the current UserTask, see {@link TaskService#complete(String)}
   * <p/>
   * Ends the current unit of work (flushes changes to process variables set
   * using {@link #setVariable(String, Object)} or made on
   * {@link BusinessProcessScoped @BusinessProcessScoped} beans).
   *
   * @throws ProcessEngineCdiException
   *           if no task is currently associated
   * @throws ProcessEngineException
   *           if the activiti command fails
   */
  /**
   * 1) verifies the task association, 2) sets local and remote variable values for the
   * task, 3) completes the task, and 4) disassociates the task from its parent process.
   */
  public void completeTask() {
    assertTaskAssociated();
    processEngine.getTaskService().setVariablesLocal(getTask().getId(), getAndClearCachedLocalVariableMap());
    processEngine.getTaskService().setVariables(getTask().getId(), getAndClearCachedVariableMap());
    processEngine.getTaskService().complete(getTask().getId());
    associationManager.disAssociate();
  }

  /**
   * @see BusinessProcess#completeTask()
   *
   * In addition this allows to end the current conversation.
   *
   */
  /**
   * completes a task and ends a conversation if the parameter `endConversation` is set
   * to true.
   * 
   * @param endConversation conclusion of a dialogue.
   */
  public void completeTask(boolean endConversation) {
    completeTask();
    if(endConversation) {
      conversationInstance.get().end();
    }
  }

  /**
   * checks if a task is associated with an object by checking the value of
   * `associationManager.getTask()`. If the task is not null, then it is associated.
   * 
   * @returns a boolean value indicating whether a task is associated with the current
   * execution.
   */
  public boolean isTaskAssociated() {
    return associationManager.getTask() != null;
  }

  /**
   * Save the currently associated task.
   *
   * @throws ProcessEngineCdiException if called from a process engine command or if no Task is currently associated.
   *
   */
  /**
   * saves a task in the Process Engine by first checking if the command context is not
   * active and if the task is associated with the process. Once these conditions are
   * met, the function calls the `saveTask` method of the `TaskService` interface to
   * save the task.
   */
  public void saveTask() {
    assertCommandContextNotActive();
    assertTaskAssociated();

    final Task task = getTask();
    // save the task
    processEngine.getTaskService().saveTask(task);
  }

  /**
   * <p>Stop working on a task. Clears the current association.</p>
   *
   * <p>NOTE: this method does not flush any changes.</p>
   * <ul>
   *  <li>If you want to flush changes to process variables, call {@link #flushVariableCache()} prior to calling this method,</li>
   *  <li>If you need to flush changes to the task object, use {@link #saveTask()} prior to calling this method.</li>
   * </ul>
   *
   * @throws ProcessEngineCdiException if called from a process engine command or if no Task is currently associated.
   */
  /**
   * disassociates a task from its command context and asserts that the task is not active.
   */
  public void stopTask() {
    assertCommandContextNotActive();
    assertTaskAssociated();
    associationManager.disAssociate();
  }

  /**
   * <p>Stop working on a task. Clears the current association.</p>
   *
   * <p>NOTE: this method does not flush any changes.</p>
   * <ul>
   *  <li>If you want to flush changes to process variables, call {@link #flushVariableCache()} prior to calling this method,</li>
   *  <li>If you need to flush changes to the task object, use {@link #saveTask()} prior to calling this method.</li>
   * </ul>
   *
   * <p>This method allows you to optionally end the current conversation</p>
   *
   * @param endConversation if true, end current conversation.
   * @throws ProcessEngineCdiException if called from a process engine command or if no Task is currently associated.
   */
  /**
   * stops a task and ends a conversation if necessary.
   * 
   * @param endConversation conversation instance which is ended if true.
   */
  public void stopTask(boolean endConversation) {
    stopTask();
    if(endConversation) {
      conversationInstance.get().end();
    }
  }

  // -------------------------------------------------

  /**
   * @param variableName
   *          the name of the process variable for which the value is to be
   *          retrieved
   * @return the value of the provided process variable or 'null' if no such
   *         variable is set
   */
  /**
   * retrieves a variable from an unspecified data source, checks if it's not null, and
   * returns its value as a specified type (T).
   * 
   * @param variableName name of a variable that is being searched for in the application's
   * theme resources.
   * 
   * @returns a reference to the specified variable's value, cast to the specified type
   * `T`.
   * 
   * 	- The output is of type `T`, which is passed as a parameter to the function.
   * 	- If the variable is not null, the value returned is the object value of the typed
   * value.
   * 	- If the variable is null, the output is set to null.
   */
  @SuppressWarnings("unchecked")
  public <T> T getVariable(String variableName) {
    TypedValue variable = getVariableTyped(variableName);
    if (variable != null) {
      Object value = variable.getValue();
      if (value != null) {
        return (T) value;
      }
    }
    return null;
  }

  /**
   * @param variableName
   *          the name of the process variable for which the value is to be
   *          retrieved
   * @return the typed value of the provided process variable or 'null' if no
   *         such variable is set
   *
   * @since 7.3
   */
  /**
   * retrieves a `TypedValue` object associated with a given `String` variable name,
   * returning its value as a generic type `T` if found, or `null` otherwise.
   * 
   * @param variableName name of a variable that is to be retrieved from the association
   * manager.
   * 
   * @returns a typed value of the specified variable name, or `null` if the variable
   * is not found.
   * 
   * 	- `T extends TypedValue`: This indicates that the returned value is of type
   * `TypedValue`, which is an object that can hold a reference to any type of data,
   * including primitive types and objects.
   * 	- `variableName`: This parameter represents the name of the variable being retrieved.
   * 	- `associationManager`: This parameter represents a component that manages
   * associations between variables and values.
   * 	- `(T) (variable)`: This line converts the `TypedValue` object to the specified
   * type `T`, which is typically a subclass of `TypedValue`. This allows for more
   * specific types to be retrieved from the variable.
   * 	- `null`: If the variable is not found, this value is returned indicating that
   * the variable does not exist or has no value associated with it.
   */
  @SuppressWarnings("unchecked")
  public <T extends TypedValue> T getVariableTyped(String variableName) {
    TypedValue variable = associationManager.getVariable(variableName);
    return variable != null ? (T) (variable) : null;
  }

  /**
   * Set a value for a process variable.
   * <p />
   *
   * <strong>NOTE:</strong> If no execution is currently associated,
   * the value is temporarily cached and flushed to the process instance
   * at the end of the unit of work
   *
   * @param variableName
   *          the name of the process variable for which a value is to be set
   * @param value
   *          the value to be set
   *
   */
  /**
   * sets a variable within an association manager based on a given name and value.
   * 
   * @param variableName name of the variable to be set.
   * 
   * @param value object that will be stored as the value of the specified variable.
   * 
   * 	- Type: The input `value` is an Object, which means it can hold any type of data.
   * 	- Method: `associationManager.setVariable()` sets a variable in the associated
   * management system.
   * 	- Parameters: Two parameters are passed to `setVariable()` - `variableName` and
   * `value`.
   */
  public void setVariable(String variableName, Object value) {
    associationManager.setVariable(variableName, value);
  }

  /**
   * Get the {@link VariableMap} of cached variables and clear the internal variable cache.
   *
   * @return the {@link VariableMap} of cached variables
   *
   * @since 7.3
   */
  /**
   * retrieves and clears the cached variables map from the association manager, creating
   * a new copy of the map for use.
   * 
   * @returns a copy of the cached variables map, which has been cleared of its contents.
   * 
   * 	- The `VariableMap` object is created by passing an existing `VariableMap` to the
   * `new VariableMapImpl()` constructor.
   * 	- The `cachedVariables` field contains a copy of the cached variables that were
   * previously associated with this association manager.
   * 	- The `clear()` method is called on the `cachedVariables` field, which removes
   * all variables from the cache.
   */
  public VariableMap getAndClearCachedVariableMap() {
    VariableMap cachedVariables = associationManager.getCachedVariables();
    VariableMap copy = new VariableMapImpl(cachedVariables);
    cachedVariables.clear();
    return copy;
  }

  /**
   * Get the map of cached variables and clear the internal variable cache.
   *
   * @return the map of cached variables
   * @deprecated use {@link #getAndClearCachedVariableMap()} instead
   */
  /**
   * retrieves a map of cached variable values and clears them.
   * 
   * @returns a map of String to Object.
   * 
   * 1/ Map type: The output is a `Map` object of type `<String, Object>`, indicating
   * that it contains key-value pairs where the keys are strings and the values can be
   * any object type.
   * 2/ Method name: The method name `getAndClearVariableCache` suggests that the
   * function returns a map containing cached variables, and also clears those caches
   * upon return.
   * 3/ Return statement: The function returns the map of cached variables, indicating
   * that the map is returned as its output.
   */
  @Deprecated
  public Map<String, Object> getAndClearVariableCache() {
    return getAndClearCachedVariableMap();
  }

  /**
   * Get a copy of the {@link VariableMap} of cached variables.
   *
   * @return a copy of the {@link VariableMap} of cached variables.
   *
   * @since 7.3
   */
  /**
   * returns a `VariableMap` object containing cached variables obtained from the
   * `associationManager.getCachedVariables()` method.
   * 
   * @returns a `VariableMap` object containing the cached variables retrieved from the
   * association manager.
   * 
   * 	- The output is of type `VariableMap`, which represents a map of variable names
   * to values that have been cached by the associated association manager.
   * 	- The map contains the cached variables retrieved from the associated association
   * manager.
   * 	- The cache is used to store the variables locally, allowing for faster access
   * and manipulation of the variables without having to retrieve them from the originating
   * source every time they are needed.
   */
  public VariableMap getCachedVariableMap() {
    return new VariableMapImpl(associationManager.getCachedVariables());
  }

  /**
   * Get a copy of the map of cached variables.
   *
   * @return a copy of the map of cached variables.
   * @deprecated use {@link #getCachedVariableMap()} instead
   */
  /**
   * retrieves a cached map of variables.
   * 
   * @returns a map of string keys to object values, retrieved from the cached variable
   * map.
   * 
   * 	- The `Map` object returned is of type `<String, Object>`.
   * 	- The map contains variable cache data that has been cached by the function.
   * 	- The keys in the map are Strings representing the names of variables, while the
   * values are Objects containing the variable values.
   */
  @Deprecated
  public Map<String, Object> getVariableCache() {
    return getCachedVariableMap();
  }

  /**
   * @param variableName
   *          the name of the local process variable for which the value is to be
   *          retrieved
   * @return the value of the provided local process variable or 'null' if no such
   *         variable is set
   */
  /**
   * retrieves a local variable from an Activity or Fragment, with type parameter `T`.
   * If the variable is found and its value is not null, it returns a instance of `T`.
   * Otherwise, it returns `null`.
   * 
   * @param variableName name of a local variable to retrieve its value.
   * 
   * @returns a typed variable of type `T`, or `null` if the variable is not found.
   * 
   * 	- `T`: The type of the variable being returned, which is inferred from the type
   * of the value being passed in the `getVariableLocalTyped` method.
   * 	- `variableName`: The name of the variable being retrieved.
   * 	- `variable`: A `TypedValue` object containing information about the variable,
   * including its type and the value it holds.
   * 	- `value`: The actual value held by the variable, which is of the same type as
   * the variable.
   */
  @SuppressWarnings("unchecked")
  public <T> T getVariableLocal(String variableName) {
    TypedValue variable = getVariableLocalTyped(variableName);
    if (variable != null) {
      Object value = variable.getValue();
      if (value != null) {
        return (T) value;
      }
    }
    return null;
  }

  /**
   * @param variableName
   *          the name of the local process variable for which the value is to
   *          be retrieved
   * @return the typed value of the provided local process variable or 'null' if
   *         no such variable is set
   *
   * @since 7.3
   */
  /**
   * retrieves a typed value associated with a given variable name, returning the typed
   * value if it exists, otherwise `null`.
   * 
   * @param variableName name of the variable to be retrieved from the association manager.
   * 
   * @returns a `TypedValue` object representing the value of the specified variable,
   * or `null` if the variable does not exist.
   * 
   * 	- `T` is the parameterized type of the return value, which represents a typed value.
   * 	- `variableName` is a string parameter representing the name of the variable to
   * be retrieved.
   * 	- `associationManager` is an object that provides access to the variable's
   * association data.
   * 	- The function returns a `TypedValue` object if the variable exists in the
   * association data, or `null` otherwise.
   */
  @SuppressWarnings("unchecked")
  public <T extends TypedValue> T getVariableLocalTyped(String variableName) {
    TypedValue variable = associationManager.getVariableLocal(variableName);
    return variable != null ? (T) variable : null;
  }

  /**
   * Set a value for a local process variable.
   * <p />
   *
   * <strong>NOTE:</strong> If a task or execution is currently associated,
   * the value is temporarily cached and flushed to the process instance
   * at the end of the unit of work - otherwise an Exception will be thrown
   *
   * @param variableName
   *          the name of the local process variable for which a value is to be set
   * @param value
   *          the value to be set
   *
   */
  /**
   * sets a variable locally in the calling class. It passes the variable name and value
   * to the associated manager for storage in the local scope.
   * 
   * @param variableName name of a variable to be set locally within the calling scope.
   * 
   * @param value object to be assigned to the variable named `variableName`.
   * 
   * The `value` argument is an instance of `Object`, which can hold any type of data.
   * It may have various attributes or properties that can be accessed using methods
   * such as `getClass()`, `getMethod()`, or `toString()`.
   * These methods can provide additional information about the object, such as its
   * class name, method names, or a string representation.
   */
  public void setVariableLocal(String variableName, Object value) {
    associationManager.setVariableLocal(variableName, value);
  }

  /**
   * Get the {@link VariableMap} of local cached variables and clear the internal variable cache.
   *
   * @return the {@link VariableMap} of cached variables
   *
   * @since 7.3
   */
  /**
   * retrieves a cached variable map from an association manager and creates a new copy
   * of it, then clears the original cache.
   * 
   * @returns a new VariableMap instance containing the same values as the cached
   * LocalVariables, but with the original values cleared.
   * 
   * 	- The VariableMap cachedVariablesLocal contains a copy of the local variables
   * that were previously cached by the association manager.
   * 	- The new VariableMap copy created in the function is an instance of the
   * VariableMapImpl class, which represents a map of variables with their values.
   * 	- The cachedVariablesLocal map is cleared in the function, effectively removing
   * any previously stored variable values.
   */
  public VariableMap getAndClearCachedLocalVariableMap() {
    VariableMap cachedVariablesLocal = associationManager.getCachedLocalVariables();
    VariableMap copy = new VariableMapImpl(cachedVariablesLocal);
    cachedVariablesLocal.clear();
    return copy;
  }

  /**
   * Get the map of local cached variables and clear the internal variable cache.
   *
   * @return the map of cached variables
   * @deprecated use {@link #getAndClearCachedLocalVariableMap()} instead
   */
  /**
   * retrieves a map of local variable cache and clears it.
   * 
   * @returns a map of string keys to object values, which is cleared after being retrieved.
   * 
   * 1/ Type: The output is a map data type, specifically a `Map` object containing
   * key-value pairs representing local variable cache entries.
   * 2/ Contents: The map contains entries for variables that are locally cached in
   * memory, with each entry representing a single variable and its associated value.
   * 3/ Lifespan: The map is created and cleared locally within the function, and its
   * contents are only valid during the execution of the function. Once the function
   * returns, the map is no longer valid.
   */
  @Deprecated
  public Map<String, Object> getAndClearVariableLocalCache() {
    return getAndClearCachedLocalVariableMap();
  }

  /**
   * Get a copy of the {@link VariableMap} of local cached variables.
   *
   * @return a copy of the {@link VariableMap} of local cached variables.
   *
   * @since 7.3
   */
  /**
   * retrieves a cached map of local variables associated with an association manager,
   * which is then returned as a new VariableMap object.
   * 
   * @returns a `VariableMap` object containing the locally cached variables.
   * 
   * 	- The output is a VariableMap object representing a cache of local variables for
   * association management.
   * 	- The map contains key-value pairs where the keys are variable names and the
   * values are associations.
   * 	- Each association is represented by an Association object, which contains
   * information about the variable pairings.
   * 	- The associations in the map are immutable, meaning they cannot be modified once
   * created.
   */
  public VariableMap getCachedLocalVariableMap() {
    return new VariableMapImpl(associationManager.getCachedLocalVariables());
  }

  /**
   * Get a copy of the map of local cached variables.
   *
   * @return a copy of the map of local cached variables.
   * @deprecated use {@link #getCachedLocalVariableMap()} instead
   */
  /**
   * retrieves a map of local variables cached through the `getCachedLocalVariableMap`
   * method.
   * 
   * @returns a map containing the local variable cache.
   * 
   * 	- It is a map containing key-value pairs with the keys being Strings and values
   * being Objects.
   * 	- The map is created using the method `getCachedLocalVariableMap()` which is deprecated.
   * 	- The contents of the map are not specified, but it can contain any data that has
   * been cached locally using the `putLocalVariable` method.
   */
  @Deprecated
  public Map<String, Object> getVariableLocalCache() {
    return getCachedLocalVariableMap();
  }

  /**
   * <p>This method allows to flush the cached variables to the Task or Execution.<p>
   *
   * <ul>
   *   <li>If a Task instance is currently associated,
   *       the variables will be flushed using {@link TaskService#setVariables(String, Map)}</li>
   *   <li>If an Execution instance is currently associated,
   *       the variables will be flushed using {@link RuntimeService#setVariables(String, Map)}</li>
   *   <li>If neither a Task nor an Execution is currently associated,
   *       ProcessEngineCdiException is thrown.</li>
   * </ul>
   *
   * <p>A successful invocation of this method will empty the variable cache.</p>
   *
   * <p>If this method is called from an active command (ie. from inside a Java Delegate).
   * {@link ProcessEngineCdiException} is thrown.</p>
   *
   * @throws ProcessEngineCdiException if called from a process engine command or if neither a Task nor an Execution is associated.
   */
  /**
   * clears variable cache entries associated with an Association Manager, which manages
   * relationships between objects in a Java application.
   */
  public void flushVariableCache() {
    associationManager.flushVariableCache();
  }

  // ----------------------------------- Getters / Setters

  /*
   * Note that Producers should go into {@link CurrentProcessInstance} in
   * order to allow for specializing {@link BusinessProcess}.
   */

  /**
   * @see #startTask(String)
   */
  /**
   * sets the Task ID for which it starts a task.
   * 
   * @param task task to be started, and when passed to the
   * `startTask()` method, it triggers the
   * invocation of that method with the task ID as
   * its argument.
   * 
   * 	- `getId()`: returns the unique identifier of the task
   * 	- ... (omitted): other properties/attributes of `task` may be accessed and
   * manipulated within the function.
   */
  public void setTask(Task task) {
    startTask(task.getId());
  }

  /**
   * @see #startTask(String)
   */
  /**
   * sets the `taskId` field of an object, triggering the `startTask` function to
   * initiate the task associated with the provided `taskId`.
   * 
   * @param taskId identity of the task being assigned to the instance, and it is used
   * to trigger the starting of the task within the method `startTask(String)`.
   */
  public void setTaskId(String taskId) {
    startTask(taskId);
  }

  /**
   * @see #associateExecutionById(String)
   */
  /**
   * associates an execution with a given ID using the `associateExecutionById()` method.
   * 
   * @param execution execution to be associated with the current instance of the class,
   * as indicated by its `id`.
   * 
   * 	- `getId()`: Returns the unique ID of the execution.
   * 	- `associateExecutionById(id)`: Associates the execution with its corresponding
   * ID.
   */
  public void setExecution(Execution execution) {
    associateExecutionById(execution.getId());
  }

  /**
   * @see #associateExecutionById(String)
   */
  /**
   * sets the execution ID of an object by associating it with a given ID.
   * 
   * @param executionId unique identifier of an execution, which is associated with the
   * current execution by the `associateExecutionById()` method call within the
   * `setExecutionId()` function.
   */
  protected void setExecutionId(String executionId) {
    associateExecutionById(executionId);
  }

  /**
   * Returns the id of the currently associated process instance or 'null'
   */
  /**
   * retrieves the process instance ID of the current execution, or returns `null` if
   * no execution is available.
   * 
   * @returns a string representing the process instance ID of the current execution,
   * or `null` if no execution is active.
   */
  public String getProcessInstanceId() {
    Execution execution = associationManager.getExecution();
    return execution != null ? execution.getProcessInstanceId() : null;
  }

  /**
   * Returns the id of the process associated with the current lie or '0'.
   */
  /**
   * retrieves the ID of a task based on the task retrieved from the `getTask()` function.
   * If no task is returned, the function returns `null`.
   * 
   * @returns a string representing the task ID if a task is present, otherwise `null`.
   */
  public String getTaskId() {
    Task task = getTask();
    return task != null ? task.getId() : null;
  }

  /**
   * Returns the currently associated {@link Task}  or 'null'
   *
   * @throws ProcessEngineCdiException
   *           if no {@link Task} is associated. Use {@link #isTaskAssociated()}
   *           to check whether an association exists.
   *
   */
  /**
   * retrieves a task object from an association manager.
   * 
   * @returns a task object.
   * 
   * 	- The output is an instance of the `Task` class, which represents a task in the
   * system.
   * 	- The `Task` class has several attributes and methods that can be used to manipulate
   * or query the task.
   * 	- The specific properties and attributes of the `Task` class depend on the
   * implementation of the `associationManager` class and the requirements of the application.
   */
  public Task getTask() {
    return associationManager.getTask();
  }

  /**
   * Returns the currently associated execution  or 'null'
   */
  /**
   * retrieves an execution object from an association manager, which is likely used
   * to manage dependencies between objects in a Java application.
   * 
   * @returns an instance of the `Execution` class.
   * 
   * The output is an instance of the `Execution` class, which represents a single
   * execution of a workflow.
   * The `Execution` object contains various attributes, such as the ID of the execution,
   * the workflow ID, the creation time, and the current state.
   * These attributes provide information about the status of the execution and can be
   * used to manage and monitor the workflow.
   */
  public Execution getExecution() {
    return associationManager.getExecution();
  }

  /**
   * @see #getExecution()
   */
  /**
   * retrieves the execution ID associated with a given execution object, returning
   * `null` if the execution is null or not found.
   * 
   * @returns a string representing the execution ID of the current execution or null
   * if no execution is currently running.
   */
  public String getExecutionId() {
    Execution e = getExecution();
    return e != null ? e.getId() : null;
  }

  /**
   * Returns the {@link ProcessInstance} currently associated or 'null'
   *
   * @throws ProcessEngineCdiException
   *           if no {@link Execution} is associated. Use
   *           {@link #isAssociated()} to check whether an association exists.
   */
  /**
   * retrieves a process instance from the process engine by its ID, checking if it's
   * not the same as the execution ID and returning the result after querying the runtime
   * service.
   * 
   * @returns a `ProcessInstance` object representing the process instance with the
   * given ID.
   * 
   * 	- The function returns a `ProcessInstance` object representing the process instance
   * associated with the given execution.
   * 	- If the input execution does not have a process instance ID that matches its ID,
   * the function creates a new process instance query and returns the single resulting
   * process instance using the execution's process instance ID.
   * 	- Otherwise, the function returns the input execution directly as a `ProcessInstance`
   * object.
   */
  public ProcessInstance getProcessInstance() {
    Execution execution = getExecution();
    if(execution != null && !(execution.getProcessInstanceId().equals(execution.getId()))){
      return processEngine
            .getRuntimeService()
            .createProcessInstanceQuery()
            .processInstanceId(execution.getProcessInstanceId())
            .singleResult();
    }
    return (ProcessInstance) execution;
  }

  // internal implementation //////////////////////////////////////////////////////////

  /**
   * checks if an execution is associated with a process instance, and throws an exception
   * if it's null.
   */
  protected void assertExecutionAssociated() {
    if (associationManager.getExecution() == null) {
      throw new ProcessEngineCdiException("No execution associated. Call busniessProcess.associateExecutionById() or businessProcess.startTask() first.");
    }
  }

  /**
   * checks if a task is associated with the process, and throws an exception if no
   * task is found.
   */
  protected void assertTaskAssociated() {
    if (associationManager.getTask() == null) {
      throw new ProcessEngineCdiException("No task associated. Call businessProcess.startTask() first.");
    }
  }

  /**
   * verifies that the Command Context is not active before allowing the method to proceed.
   */
  protected void assertCommandContextNotActive() {
    if(Context.getCommandContext() != null) {
      throw new ProcessEngineCdiException("Cannot use this method of the BusinessProcess bean from an active command context.");
    }
  }

}
