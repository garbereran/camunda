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
 * provides various methods for managing the association between a task and an
 * execution, as well as flushing the cached variables to the Task or Execution. It
 * also provides getters and setters for the associated task and execution, and allows
 * to flush the cached variables to the Task or Execution. The class also provides
 * internal implementation details, such as assertions for checking the association
 * status of tasks and executions, and a check for active command context.
 */
@Named
public class BusinessProcess implements Serializable {

  private static final long serialVersionUID = 1L;

  @Inject private ProcessEngine processEngine;

  @Inject private ContextAssociationManager associationManager;

  @Inject private Instance<Conversation> conversationInstance;

  /**
   * starts a process instance with the given ID, checks if it's already ended, and
   * sets the execution status accordingly.
   * 
   * @param processDefinitionId ID of the process definition to start.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `processInstance`: This is an instance of the `ProcessInstance` class, representing
   * a running process in the engine.
   * 	- `isEnded()`: If set to `true`, it means that the process instance has been ended
   * successfully, otherwise it's still running.
   * 	- `getAndClearCachedVariableMap()`: This method clears the cache of variable maps
   * for the current process instance.
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
   * starts a process instance by its ID and business key, checks if it's already ended,
   * and sets the execution context if necessary.
   * 
   * @param processDefinitionId unique identifier of the process definition to be started.
   * 
   * @param businessKey business key of the process definition being started, which is
   * used to identify the specific process instance being initiated.
   * 
   * @returns a `ProcessInstance` object representing the started process instance.
   * 
   * 	- `instance`: The ProcessInstance object representing the started process instance.
   * 	- `isEnded()`: Indicates whether the process instance is ended or not. If the
   * method returns an instance that is not ended, then it means that the process
   * instance has been started successfully.
   * 
   * The function `assertCommandContextNotActive()` is used to ensure that the command
   * context is not active when calling the `startProcessInstanceById` method. This is
   * important because the command context can interfere with the proper execution of
   * the process instance.
   * 
   * The `getAndClearCachedVariableMap()` method is used to retrieve and clear any
   * cached variable map associated with the process definition ID and business key.
   * This is done to ensure that the correct variable map is used when starting the
   * process instance.
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
   * starts a process instance with the specified ID and variables, retrieves the latest
   * version of the process definition, and sets the execution of the process instance.
   * 
   * @param processDefinitionId identifier of the process definition that is to be started.
   * 
   * @param variables map of initial variables for the started process.
   * 
   * 	- Map<String, Object>: This parameter represents a map of string keys to object
   * values, where each key-value pair represents a variable in the process instance.
   * The objects can be any type that can be serialized and deserialized.
   * 	- assertCommandContextNotActive(): This assertion ensures that the command context
   * is not active before starting the process instance. It is used to prevent unintended
   * interactions with other commands or process instances.
   * 	- getAndClearCachedVariableMap(): This method retrieves and clears the cached
   * variable map, which is used to store the variables for the current process instance.
   * It ensures that the variables are only accessed once during the execution of the
   * function.
   * 
   * @returns a `ProcessInstance` object representing the started process instance.
   * 
   * 	- `ProcessInstance instance`: This is an object representing the started process
   * instance. It contains information about the process instance, such as its ID, name,
   * and current state.
   * 	- `isEnded()`: This method returns a boolean indicating whether the process
   * instance has ended or not. If the process instance has ended, it means that it has
   * completed its execution and no further actions can be taken on it.
   * 	- `setExecution()`: This method sets the execution of the process instance to the
   * provided instance. It is used to associate the process instance with a specific
   * execution, which can be useful for tracking the state of the process instance over
   * time.
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
   * starts a process instance with the given `processDefinitionId`, `businessKey`, and
   * custom variables. It retrieves the cached variables, puts them into the process
   * instance, and sets the execution status to indicate that it has been started.
   * 
   * @param processDefinitionId identifier of the process definition to start.
   * 
   * @param businessKey unique identifier of the business process to be started.
   * 
   * @param variables map of variables to be passed to the started process instance,
   * which are then added to the cache and used to initialize the process instance.
   * 
   * 	- `Map<String, Object> variables`: This is a map that contains key-value pairs
   * representing the input variables for the process instance. The keys are strings,
   * and the values can be any type of object.
   * 	- `processDefinitionId`: The ID of the process definition to start.
   * 	- `businessKey`: The business key of the process instance to start.
   * 	- `getAndClearCachedVariableMap()`: This is a method that retrieves the cached
   * variable map and clears it, which is necessary before starting a new process instance.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `instance`: A `ProcessInstance` object representing the started process instance.
   * 	- `isEnded()`: Indicates whether the process instance has ended or not. If it
   * has, the method sets the execution to indicate that the process is over.
   * 
   * The output of the function is an instance of the `ProcessInstance` class, which
   * contains information about the started process instance, including its ID, name,
   * and current state.
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
   * starts a process instance using its key, checks if it is already ended, and sets
   * the execution context to the started instance if not.
   * 
   * @param key process instance key that is being started.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `ProcessInstance instance`: This is the ProcessInstance object that represents
   * the started process. It has various attributes and methods that can be used to
   * interact with the process instance.
   * 	- `isEnded()`: This method returns a boolean value indicating whether the process
   * instance is ended or not. If the process instance is ended, it means that the
   * process has completed successfully or failed.
   * 	- `getAndClearCachedVariableMap()`: This method retrieves and clears any cached
   * variable map for the process instance. The variable map contains information about
   * the variables in the process instance, which can be useful for further processing
   * or analysis.
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
   * starts a process instance by key and retrieves it from the process engine. It also
   * sets the execution status to indicate that the process has been started.
   * 
   * @param key process instance key to start.
   * 
   * @param businessKey business key of the process instance to be started, which is
   * used to identify the process instance uniquely within the context of the application.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `instance`: A `ProcessInstance` object representing the started process instance.
   * 	- `isEnded`: Whether the process instance is ended or not. If it's not ended, the
   * execution is set.
   * 
   * The `startProcessByKey` function first asserts that the command context is not
   * active before starting the process instance using the `getRuntimeService()` method
   * and calling the `startProcessInstanceByKey()` method to retrieve the started process
   * instance. The `getAndClearCachedVariableMap()` method is then called to clear any
   * cached variable map.
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
   * starts a process instance by key and sets the execution status to ongoing if the
   * instance is not ended.
   * 
   * @param key process definition key used to start a new process instance.
   * 
   * @param variables map of variable values to be used when starting a process instance
   * by its key, and is passed to the `startProcessInstanceByKey()` method of the
   * `processEngine` to provide the necessary context for the process instance startup.
   * 
   * 	- `Map<String, Object> variables`: This map contains key-value pairs representing
   * the variables to be passed to the start process instance. Each key corresponds to
   * a variable name, and the value is the actual value of that variable. The values
   * can be any valid Java object type, such as integers, strings, dates, or other
   * complex data types.
   * 	- `assertCommandContextNotActive()`: This method checks if the command context
   * is already active, and if so, it throws an exception to prevent multiple instances
   * from being created.
   * 	- `getAndClearCachedVariableMap()`: This method retrieves a copy of the cached
   * variable map and then clears the cache to ensure that only the latest variables
   * are used for the start process instance.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `ProcessInstance`: This is the object representing the started process instance.
   * It contains information such as the process ID, the process definition ID, and the
   * current state of the process instance.
   * 	- `isEnded()`: This method checks whether the process instance has ended or not.
   * If it has ended, the method returns `true`, otherwise it returns `false`.
   * 	- `getExecution()`: This method retrieves the execution of the started process
   * instance. It is a wrapper around the actual execution object, which provides access
   * to various information related to the execution, such as the ID, the current state,
   * and the like.
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
   * starts a process instance by its key and initializes variables with provided map.
   * It then checks if the process instance is already ended, sets execution to it and
   * returns the instance if not.
   * 
   * @param key unique process instance identifier that is used to start a new instance
   * of a process.
   * 
   * @param businessKey unique identifier of the process instance to start, which is
   * used to locate the appropriate process definition and activate its execution.
   * 
   * @param variables map of variables to be passed to the startProcessInstanceByKey
   * method, which is used to initialize the process instance with the specified key
   * and business key.
   * 
   * 	- `String key`: The unique process instance ID to start.
   * 	- `String businessKey`: The business key identifying the process instance.
   * 	- `Map<String, Object> variables`: A map of variable names and values passed from
   * the caller to customize the process instance startup. The keys in the map are
   * case-sensitive strings, while the values can be any Java object type.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `instance`: The ProcessInstance object representing the started process instance.
   * 	- `isEnded()`: A boolean indicating whether the process instance is ended or not.
   * If it is ended, then the execution is set to the instance.
   * 
   * The function first asserts that the command context is not active before starting
   * the process instance using the `getRuntimeService().startProcessInstanceByKey`
   * method and passing in the key, business key, and cached variables. After starting
   * the instance, it checks whether the instance is ended and sets the execution to
   * the instance if it is not ended. The returned output is an instance of `ProcessInstance`.
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
   * starts a process instance by sending a message to the process engine. It retrieves
   * and clears any cached variables, passes the message name to the runtime service
   * for processing, and sets the execution state of the resulting process instance.
   * 
   * @param messageName name of the message to be started.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `ProcessInstance`: The instance of the process that was started by the message.
   * 	- `isEnded()`: A boolean indicating whether the process instance has ended or
   * not. If it has ended, the method will have set `execution` to the instance.
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
   * starts a process instance by message name and provides process variables to the
   * runtime service, which then creates and starts the instance if not ended.
   * 
   * @param messageName name of the message that triggers the start of the process instance.
   * 
   * @param processVariables map of variables that will be used to start the process instance.
   * 
   * 	- `String messageName`: The name of the message to start the process instance for.
   * 	- `Map<String, Object> processVariables`: A map containing the variables that
   * will be passed to the process instance when it is started.
   * 
   * @returns a `ProcessInstance` object representing the started process.
   * 
   * 	- `instance`: A `ProcessInstance` object representing the newly started process
   * instance.
   * 	- `isEnded()`: A boolean indicating whether the process instance has ended or
   * not. If it has ended, the instance is set to `true`, otherwise it is set to `false`.
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
   * starts a process instance by sending a message with the specified name and business
   * key, passing in the necessary process variables and retrieving the resulting process
   * instance.
   * 
   * @param messageName name of the message to start the process instance with.
   * 
   * @param businessKey unique identifier of the business process to which the message
   * belongs.
   * 
   * @param processVariables variable values that will be passed to the started process
   * instance, which can be used to configure or customize the process execution.
   * 
   * 	- `String messageName`: The name of the message to start a process instance for.
   * 	- `String businessKey`: A unique identifier for the process instance.
   * 	- `Map<String, Object> processVariables`: A map of variables passed from the
   * caller to the function, which are then used to initialize the new process instance.
   * 
   * @returns a ProcessInstance object representing the newly started process.
   * 
   * 	- `ProcessInstance instance`: This is an object representing a process instance
   * that has been started by the message. It contains information about the process
   * instance such as its ID, name, and current state.
   * 	- `isEnded()`: This is a boolean property that indicates whether the process
   * instance has ended or not. If the process instance is ended, then the function has
   * successfully completed.
   * 
   * The output of the function can be destructured by accessing the properties of the
   * `ProcessInstance` object using dot notation. For example, to access the ID of the
   * process instance, one can use `instance.getId()`.
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
   * retrieves an execution instance from the process engine based on its ID, and sets
   * it as the current execution managed by the association manager.
   * 
   * @param executionId ID of the execution to be associated with the given
   * association manager.
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
   * checks if an execution ID is provided by association manager. If it is, the function
   * returns `true`, otherwise it returns `false`.
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
   * asserts execution association, sets local variables on a process instance, signals
   * the associated event, and disassociates the execution from its parent process.
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
   * signals the end of a conversation instance, if the argument `endConversation` is
   * true.
   * 
   * @param endConversation conclusion of a conversation, which is implemented by calling
   * the `end()` method on the `conversationInstance`.
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
   * retrieves a task by ID, creates a new task if none exists with that ID, and
   * associates the execution with the task.
   * 
   * @param taskId id of the task to be resumed, and it is used to retrieve the task
   * from the process engine or to create a new task if it does not exist.
   * 
   * @returns a reference to the task with the specified ID, or an exception if the
   * task does not exist.
   * 
   * The function returns a `Task` object, which represents a task instance in the
   * process engine. The `Task` object has several attributes, including `id`, `name`,
   * `description`, `owner`, `priority`, and `status`.
   * 
   * The `id` attribute is a unique identifier for the task, while `name` and `description`
   * provide a human-readable name and description of the task. The `owner` attribute
   * specifies the user who created or assigned the task, while `priority` represents
   * the task's priority level. Finally, the `status` attribute indicates the current
   * state of the task, such as "active" or "completed".
   * 
   * Overall, the `startTask` function returns a Task object that contains essential
   * information about the task instance being started.
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
   * begins a task conversation if necessary and then starts the task with the specified
   * ID.
   * 
   * @param taskId unique identifier for the task to be started.
   * 
   * @param beginConversation state of conversations, with a value of `true` indicating
   * that a conversation should be initiated if it is transient, and a value of `false`
   * otherwise.
   * 
   * @returns a Task object, which represents the task to be executed.
   * 
   * 	- The task ID is passed as an argument in the function call.
   * 	- The function checks whether the conversation is transient or not before beginning
   * it.
   * 	- If the conversation is transient, the function begins it.
   * 	- The function then returns the `startTask` method's output, which is a Task object.
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
   * completes a task by setting local and inherited variable values, disassociating
   * an associated task, and marking the task as completed in the process engine.
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
   * completes a task and ends a conversation if the parameter `endConversation` is true.
   * 
   * @param endConversation conclusion of an ongoing conversation, triggering the
   * termination of the related conversation instance when set to `true`.
   */
  public void completeTask(boolean endConversation) {
    completeTask();
    if(endConversation) {
      conversationInstance.get().end();
    }
  }

  /**
   * checks if a task is associated with an entity by checking the value returned by
   * the `associationManager.getTask()` method.
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
   * validates that a command context is not active and that a task is associated with
   * the current process instance. It then saves the task to the Process Engine's Task
   * Service.
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
   * 1) ensures the command context is not active, 2) verifies the task is associated
   * with a command, and 3) disassociates the task from the command.
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
   * stops a task and ends a conversation, respectively.
   * 
   * @param endConversation conversation instance which will end if it's set to `true`.
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
   * retrieves a variable from a source and returns it as an object of type `T`. If the
   * variable is not found or its value is null, the function returns `null`.
   * 
   * @param variableName name of a variable that is retrieved from the component's state.
   * 
   * @returns a typed value of type `T`, or `null` if the variable is not found.
   * 
   * The function returns an object of type `T`, which can be any subtype of `T`.
   * The function first checks if the `TypedValue` object returned by `getVariableTyped`
   * is null. If it is not null, the function proceeds to check if the value property
   * of the TypedValue object is not null. If it is not null, the function returns a
   * casted version of the value to the desired type `T`.
   * If either of these conditions is true, the function returns an instance of `T`.
   * Otherwise, the function returns `null`.
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
   * casting it to the specified type (`T`) if found, or returning `null` otherwise.
   * 
   * @param variableName name of the variable to be retrieved from the association manager.
   * 
   * @returns a TypedValue object of the specified type for the given variable name,
   * or null if the variable is not found.
   * 
   * The function returns a `T` object, which represents a typed value.
   * The input parameter `variableName` is used to retrieve a `TypedValue` object from
   * the `associationManager`.
   * If the `TypedValue` object is null, the function returns `null` (the absence of
   * any value).
   * Otherwise, the `TypedValue` object is cast to a `T` object using a narrowing conversion.
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
   * sets a variable in an association manager based on a given name and value.
   * 
   * @param variableName name of a variable that is being set by the `associationManager`.
   * 
   * @param value object that will be stored as the value of the specified variable.
   * 
   * 	- `Object value`: The object that contains the variable name and the value to be
   * set.
   * 	- `String variableName`: The name of the variable being set.
   * 	- `associationManager`: An instance of an association manager, which manages the
   * setting of variables.
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
   * retrieves a copy of the cached variables, clears them, and returns the copy.
   * 
   * @returns a copy of the cached variables, which are then cleared from the cache.
   * 
   * 1/ The returned output is a `VariableMap` instance, which represents a cache of
   * variables associated with an association manager.
   * 2/ The cache contains variable maps that have been previously fetched from the
   * association manager using the `getCachedVariables` method.
   * 3/ The returned map has the same properties and attributes as the original cached
   * variables, including any associations or relationships between variables.
   * 4/ The map is immutable, meaning its contents cannot be modified once it is created.
   * 5/ The map is also read-only, meaning it cannot be modified through any means.
   * 6/ The `getAndClearCachedVariableMap` function clears the cache of the variable
   * map after returning a copy of it, ensuring that the cache remains up-to-date and
   * efficient.
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
   * retrieves and removes a cache of variable values.
   * 
   * @returns a map of cached variable values, which are cleared after retrieval.
   * 
   * 	- The output is a map containing key-value pairs, where the keys are Strings and
   * the values can be any type of object.
   * 	- The map is returned by the function after getting variable cache and clearing
   * it.
   * 	- The function provides access to the variable cache through the map, allowing
   * for efficient retrieval and removal of cached variables.
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
   * retrieves a cached map of variables from the association manager and returns it
   * as a `VariableMapImpl`.
   * 
   * @returns a `VariableMap` object containing the cached variables retrieved from the
   * association manager.
   * 
   * 	- The output is a `VariableMapImpl` object. This class represents a map of
   * variables, where each variable has an associated value.
   * 	- The ` VariableMapImpl` object is created by calling the `getCachedVariables()`
   * method of the `associationManager`. This method returns a collection of `Variable`
   * objects that represent the variables associated with the current instance of the
   * `AssociationManager`.
   * 	- The returned `VariableMapImpl` object contains the same set of variables as the
   * original `associationManager`, but the values may be cached or loaded from storage.
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
   * retrieves a cached map containing variable values.
   * 
   * @returns a map of strings to objects.
   * 
   * The returned map contains key-value pairs representing variable cache entries. The
   * keys are Strings, while the values can be of any type.
   * 
   * The map is immutable and thread-safe, meaning its contents cannot be changed once
   * created, and it can be accessed concurrently by multiple threads without the need
   * for synchronization.
   * 
   * Overall, this function provides a convenient way to retrieve variable cache entries
   * in an organized manner.
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
   * retrieves a variable from local storage with the given name and returns its value
   * as an instance of the specified type (`T`). If the variable is null, it returns a
   * null value of the same type.
   * 
   * @param variableName name of a local variable that is to be retrieved.
   * 
   * @returns a `T` object representing the value of the specified variable, or `null`
   * if the variable is not found or its value is ` null`.
   * 
   * 	- The output is of type `T`, which is a generic type parameter.
   * 	- The function returns an object of type `T` if the variable exists and its value
   * is not null. Otherwise, it returns `null`.
   * 	- The function uses the `TypedValue` class to retrieve the variable's value, which
   * ensures that the correct type is returned based on the variable's declared type.
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
   * retrieves a `TypedValue` object associated with a given variable name, and returns
   * it as a specified type (`T`). If the variable is not found, it returns `null`.
   * 
   * @param variableName name of the variable to be retrieved from the association manager.
   * 
   * @returns a typed value of the specified type for the given variable name, or `null`
   * if the variable is not found.
   * 
   * 	- `T extends TypedValue`: This indicates that the return type is a subclass of
   * `TypedValue`, which suggests that the function may return a custom or specialized
   * typed value.
   * 	- `<T extends TypedValue>`: This type parameter indicates that the function can
   * return any type that extends `TypedValue`.
   * 	- `getVariableLocalTyped(String variableName)`: This is the method name, which
   * describes the purpose of the function as retrieving a local variable with the given
   * name.
   * 	- `TypedValue variable = associationManager.getVariableLocal(variableName);`:
   * This line declares the variable `variable` and assigns its value to it using the
   * `getVariableLocal` method provided by the `associationManager`.
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
   * sets a local variable in the associated manager instance.
   * 
   * @param variableName name of a local variable to be set by the `associationManager`.
   * 
   * @param value object that will be set as the local variable with the given `variableName`.
   * 
   * 	- The type of `value` is determined by its class hierarchy, which can be a subclass
   * of `java.io.Serializable`.
   * 	- If `value` is an instance of a serializable class, then it can be deserialized
   * from a binary stream or string representation using the `readObject()` method of
   * the `ObjectStreamClass` class.
   * 	- The `value` object may also have additional attributes and methods defined in
   * its class hierarchy, which can be accessed and used as needed within the function.
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
   * retrieves and returns a copy of the local variable map from the association manager,
   * while also clearing the original map to avoid accumulating unnecessary data.
   * 
   * @returns a new VariableMap containing the local variables from the cache, with any
   * previously cached local variables cleared.
   * 
   * 	- `VariableMap cachedVariablesLocal`: A map that stores the local variables
   * associated with their corresponding values.
   * 	- `VariableMap copy`: A new instance of `VariableMapImpl`, created by copying the
   * contents of `cachedVariablesLocal`.
   * 	- `cachedVariablesLocal.clear()`: Clears the contents of `cachedVariablesLocal`,
   * effectively removing all the local variables associated with their values.
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
   * retrieves a map of local variable cache and clears it after use.
   * 
   * @returns a map of strings to objects.
   * 
   * 	- Map: The output is of type `Map`, which is an object that stores key-value pairs.
   * 	- String keys: The keys in the map are strings.
   * 	- Object values: The values in the map can be any object type.
   * 	- getAndClearCachedLocalVariableMap(): This method is used to retrieve a cache
   * of local variables, and then clear it.
   * 
   * The properties of the output are summarized above.
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
   * returns a cached map of local variables associated with an association manager.
   * 
   * @returns a `VariableMap` object containing the cached local variables.
   * 
   * 	- The VariableMap object is created using the `new VariableMapImpl` constructor.
   * 	- The map contains the cached local variables retrieved from the `associationManager`.
   * 	- The map is an immutable instance, meaning it cannot be modified once created.
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
   * retrieves a map of local variables cached using the `getCachedLocalVariableMap()`
   * method.
   * 
   * @returns a map of strings to objects containing locally cached variables.
   * 
   * 	- The function returns a map of type String to Object.
   * 	- The map contains local variable cache data.
   * 	- The map is generated by calling another function named `getCachedLocalVariableMap()`.
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
   * clears the variable cache managed by the association manager, releasing any cached
   * data and improving performance by reducing the load on memory.
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
   * sets the value of the `Task` object to a new reference, and starts the associated
   * task by calling the `startTask` function with the task ID as an argument.
   * 
   * @param task `Task` object to be started.
   * 
   * 	- `getId()` returns the unique identifier of the task.
   */
  public void setTask(Task task) {
    startTask(task.getId());
  }

  /**
   * @see #startTask(String)
   */
  /**
   * sets the `taskId` field of an object, triggering the `startTask` function to begin
   * the task associated with the given `taskId`.
   * 
   * @param taskId identifier of a task to be started by the `startTask()` method call
   * within the `setTaskId()` function.
   */
  public void setTaskId(String taskId) {
    startTask(taskId);
  }

  /**
   * @see #associateExecutionById(String)
   */
  /**
   * associates an execution with the system by using its ID.
   * 
   * @param execution execution to be associated with the method caller, and is linked
   * to its identifier through the `associateExecutionById()` method call.
   * 
   * 	- The `getId()` method returns the unique identifier for this execution instance.
   */
  public void setExecution(Execution execution) {
    associateExecutionById(execution.getId());
  }

  /**
   * @see #associateExecutionById(String)
   */
  /**
   * sets an execution ID for a task.
   * 
   * @param executionId unique identifier for an execution within the system, which is
   * associated with the current instance of the object by the `associateExecutionById()`
   * method call.
   */
  protected void setExecutionId(String executionId) {
    associateExecutionById(executionId);
  }

  /**
   * Returns the id of the currently associated process instance or 'null'
   */
  /**
   * returns the process instance ID of a given execution, or `null` if the execution
   * is not provided.
   * 
   * @returns a string representing the process instance ID of the executing process.
   */
  public String getProcessInstanceId() {
    Execution execution = associationManager.getExecution();
    return execution != null ? execution.getProcessInstanceId() : null;
  }

  /**
   * Returns the id of the process associated with the current lie or '0'.
   */
  /**
   * returns the ID of a task associated with the current user, retrieved from a database
   * or other data source. If no task is found, the function returns `null`.
   * 
   * @returns a string representing the task ID of the task retrieved from the `getTask`
   * method, or `null` if no task was retrieved.
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
   * retrieves a task from an association manager.
   * 
   * @returns a task object retrieved from the association manager.
   * 
   * 	- The function returns an instance of the `Task` class.
   * 	- The `Task` object represents a task that is associated with an association manager.
   * 	- The object has various attributes, such as a name, description, and due date.
   */
  public Task getTask() {
    return associationManager.getTask();
  }

  /**
   * Returns the currently associated execution  or 'null'
   */
  /**
   * retrieves an execution object from the association manager, indicating that it
   * returns a reference to an execution object.
   * 
   * @returns an execution object representing the current state of the application's
   * execution.
   * 
   * 	- The `associationManager` field represents an instance of the `AssociationManager`
   * class, which manages associations between objects in the program.
   * 	- The `getExecution()` method returns the current execution associated with the
   * `associationManager`.
   * 	- The execution object contains information about the current execution, such as
   * its ID, started time, and status.
   */
  public Execution getExecution() {
    return associationManager.getExecution();
  }

  /**
   * @see #getExecution()
   */
  /**
   * retrieves the execution ID of a given execution, returning `null` if the execution
   * is null.
   * 
   * @returns a string representing the execution ID of the current execution, or `null`
   * if no execution ID is available.
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
   * retrieves a process instance associated with an execution, either by Id or by
   * process instance Id. If no matching process instance is found, it creates a new
   * one based on the execution's information.
   * 
   * @returns a `ProcessInstance` object representing the process instance identified
   * by the `execution.getProcessInstanceId()`.
   * 
   * 	- The function first checks whether the execution ID matches the process instance
   * ID. If it doesn't, then a query is created to retrieve the process instance with
   * the matching ID.
   * 	- The function returns a single result from the query using the `singleResult()`
   * method.
   * 	- The output is a `ProcessInstance` object representing the process instance with
   * the matching ID.
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
   * verifies if an execution is associated with a process instance, and raises a
   * `ProcessEngineCdiException` if not.
   */
  protected void assertExecutionAssociated() {
    if (associationManager.getExecution() == null) {
      throw new ProcessEngineCdiException("No execution associated. Call busniessProcess.associateExecutionById() or businessProcess.startTask() first.");
    }
  }

  /**
   * verifies that a task is associated with the process instance, and if not, throws
   * an exception indicating to call `businessProcess.startTask()` first.
   */
  protected void assertTaskAssociated() {
    if (associationManager.getTask() == null) {
      throw new ProcessEngineCdiException("No task associated. Call businessProcess.startTask() first.");
    }
  }

  /**
   * checks if the `Context.getCommandContext()` is null, and if it isn't, it throws a
   * `ProcessEngineCdiException`.
   */
  protected void assertCommandContextNotActive() {
    if(Context.getCommandContext() != null) {
      throw new ProcessEngineCdiException("Cannot use this method of the BusinessProcess bean from an active command context.");
    }
  }

}
