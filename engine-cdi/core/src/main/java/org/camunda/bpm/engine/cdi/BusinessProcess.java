
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


@Named
public class BusinessProcess implements Serializable {

  private static final long serialVersionUID = 1L;

  @Inject private ProcessEngine processEngine;

  @Inject private ContextAssociationManager associationManager;

  @Inject private Instance<Conversation> conversationInstance;

  /**
   * starts a process instance with the provided process definition ID and sets the
   * execution variable for the started instance.
   * 
   * @param processDefinitionId unique identifier of the process definition that is to
   * be started.
   * 
   * @returns a `ProcessInstance` object containing information about the started process.
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
   * starts a process instance with the given ID and business key using the process
   * engine's `startProcessInstanceById` method, sets the execution variable, and returns
   * the started instance.
   * 
   * @param processDefinitionId id of the process definition to start.
   * 
   * @param businessKey business key of the process instance to be started, which is
   * used to identify the specific process instance to be executed by the
   * `startProcessInstanceById` method.
   * 
   * @returns a `ProcessInstance` object representing the started process.
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
   * starts a process instance with the given ID using the process engine's
   * `startProcessInstanceById` method. It also sets the execution of the instance if
   * it is not already ended.
   * 
   * @param processDefinitionId ID of the process definition to start.
   * 
   * @param variables map of variables to be passed to the started process instance,
   * which are then made available to the process instance through its variable scope.
   * 
   * @returns a `ProcessInstance` object containing information about the started process.
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
   * starts a process instance by its ID, passing variables to be used as context and
   * setting the execution.
   * 
   * @param processDefinitionId id of the process definition to start.
   * 
   * @param businessKey unique identifier of the business process that the given process
   * definition belongs to, which is required for starting the process instance in ProcessKit.
   * 
   * @param variables map of variables to be passed to the started process instance,
   * which is used to update the cache of variable maps and then provided to the
   * `startProcessInstanceById` method.
   * 
   * @returns a `ProcessInstance` object representing the started process.
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
   * starts a process instance by its key, verifies the command context is not active,
   * retrieves the instance from the engine, sets the execution variable if it's not
   * ended, and returns the instance.
   * 
   * @param key process instance key to start.
   * 
   * @returns a `ProcessInstance` object representing the started process.
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
   * starts a process instance by its key and business key, checks if it is ended, and
   * sets the execution variable to the returned instance if it is not ended.
   * 
   * @param key unique process instance ID to start.
   * 
   * @param businessKey unique identifier of the business process associated with the
   * given process instance key, which is used to retrieve the correct process instance
   * from the process engine.
   * 
   * @returns a `ProcessInstance` object representing the started process instance.
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
   * starts a process instance by key, using the given variables and checking if the
   * instance is already ended before setting its execution status.
   * 
   * @param key process instance key that is being activated or started.
   * 
   * @param variables map of variable values that will be used to start the process instance.
   * 
   * @returns a `ProcessInstance` object representing the started process.
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
   * starts a process instance by key, updating the variables cache and setting the
   * execution state if necessary. It returns the started instance.
   * 
   * @param key unique process instance key that identifies the specific process instance
   * to be started.
   * 
   * @param businessKey business key of the process instance to be started, which is
   * used to identify the process instance to be started in the process engine.
   * 
   * @param variables map of variables to pass to the ProcessInstance when starting it
   * by key, which can be used to initialize the process instance with additional data.
   * 
   * @returns a `ProcessInstance` object representing the started process.
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
   * starts a process instance by sending a message to the process engine, using the
   * given message name and caching any variables retrieved from the context. It then
   * sets the execution status of the created instance to "in progress" or "ended",
   * depending on whether it was successfully started or not.
   * 
   * @param messageName message to be executed when starting a process instance using
   * the `startProcessInstanceByMessage()` method of the process engine.
   * 
   * @returns a ProcessInstance object representing the started process.
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
   * starts a new process instance by sending a message to the process engine, using
   * the provided message name and process variables.
   * 
   * @param messageName name of the message to be started as a process instance.
   * 
   * @param processVariables variable values that will be used to start a process
   * instance when the `startProcessByMessage` function is called.
   * 
   * @returns a `ProcessInstance` object representing the started process.
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
   * starts a process instance by sending a message to the process engine, passing in
   * the name of the message, the business key, and any necessary process variables.
   * The function retrieves the cached variable map, adds any new process variables,
   * and then calls the `startProcessInstanceByMessage` method on the process engine
   * to start the instance.
   * 
   * @param messageName name of the message that will be triggered when the process is
   * started.
   * 
   * @param businessKey business key of the process instance that is being started,
   * which is used to identify the process instance within the process engine.
   * 
   * @param processVariables map of process variables that will be used to start the
   * process instance.
   * 
   * @returns a `ProcessInstance` object representing the started process.
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
   * retrieves an execution from the process engine based on its ID, sets it as the
   * association manager's execution, and throws a `ProcessEngineCdiException` if no
   * matching execution is found.
   * 
   * @param executionId unique identifier of the execution to be associated with the
   * current execution.
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

  public boolean isAssociated() {
    return associationManager.getExecutionId() != null;
  }

  public void signalExecution() {
    assertExecutionAssociated();
    processEngine.getRuntimeService().setVariablesLocal(associationManager.getExecutionId(), getAndClearCachedLocalVariableMap());
    processEngine.getRuntimeService().signal(associationManager.getExecutionId(), getAndClearCachedVariableMap());
    associationManager.disAssociate();
  }

  /**
   * triggers the termination of a conversation instance upon receiving the signal.
   * 
   * @param endConversation conclusion of the conversation, triggering the execution
   * to end when set to `true`.
   */
  public void signalExecution(boolean endConversation) {
    signalExecution();
    if(endConversation) {
      conversationInstance.get().end();
    }
  }

  // -------------------------------------

  /**
   * retrieves a task by ID, creates a new task if none exists with the provided ID,
   * and associates the execution with the newly created or retrieved task.
   * 
   * @param taskId id of the task to be resumed.
   * 
   * @returns a reference to the specified task.
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
   * starts a task identified by `taskId`. If `beginConversation` is `true`, it also
   * begins a conversation if one is not already running.
   * 
   * @param taskId unique identifier of the task to be started.
   * 
   * @param beginConversation begin conversation method of the Conversation instance
   * when it is called with true value.
   * 
   * @returns a task instance.
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


  public void completeTask() {
    assertTaskAssociated();
    processEngine.getTaskService().setVariablesLocal(getTask().getId(), getAndClearCachedLocalVariableMap());
    processEngine.getTaskService().setVariables(getTask().getId(), getAndClearCachedVariableMap());
    processEngine.getTaskService().complete(getTask().getId());
    associationManager.disAssociate();
  }


  /**
   * performs a task and ends a conversation if the argument `endConversation` is true.
   * 
   * @param endConversation boolean value that determines whether or not to end a
   * conversation instance when the `completeTask()` method is called.
   */
  public void completeTask(boolean endConversation) {
    completeTask();
    if(endConversation) {
      conversationInstance.get().end();
    }
  }

  public boolean isTaskAssociated() {
    return associationManager.getTask() != null;
  }


  public void saveTask() {
    assertCommandContextNotActive();
    assertTaskAssociated();

    final Task task = getTask();
    // save the task
    processEngine.getTaskService().saveTask(task);
  }


  public void stopTask() {
    assertCommandContextNotActive();
    assertTaskAssociated();
    associationManager.disAssociate();
  }


  /**
   * stops a task and ends a conversation, if requested.
   * 
   * @param endConversation whether the task should end the conversation or not.
   */
  public void stopTask(boolean endConversation) {
    stopTask();
    if(endConversation) {
      conversationInstance.get().end();
    }
  }

  // -------------------------------------------------


  /**
   * retrieves a variable's value of specified type from the application context,
   * returning the value as the requested type if found, or `null` otherwise.
   * 
   * @param variableName name of a variable to be retrieved from the application's
   * variables cache.
   * 
   * @returns a reference of type `T` to the value stored under the specified variable
   * name, or `null` if the variable does not exist or has no value.
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
   * retrieves a `TypedValue` associated with a given `String` variable name, and returns
   * it as a parameterized type `T`. If the variable is null, the function returns `null`.
   * 
   * @param variableName name of the variable to be retrieved from the association manager.
   * 
   * @returns a `TypedValue` object representing the specified variable, or `null` if
   * it cannot be found.
   */
  @SuppressWarnings("unchecked")
  public <T extends TypedValue> T getVariableTyped(String variableName) {
    TypedValue variable = associationManager.getVariable(variableName);
    return variable != null ? (T) (variable) : null;
  }


  /**
   * sets a variable's value provided by the user.
   * 
   * @param variableName name of a variable that the `associationManager` will set the
   * value of.
   * 
   * @param value object to be associated with the specified variable name.
   */
  public void setVariable(String variableName, Object value) {
    associationManager.setVariable(variableName, value);
  }


  public VariableMap getAndClearCachedVariableMap() {
    VariableMap cachedVariables = associationManager.getCachedVariables();
    VariableMap copy = new VariableMapImpl(cachedVariables);
    cachedVariables.clear();
    return copy;
  }


  @Deprecated
  public Map<String, Object> getAndClearVariableCache() {
    return getAndClearCachedVariableMap();
  }


  public VariableMap getCachedVariableMap() {
    return new VariableMapImpl(associationManager.getCachedVariables());
  }


  @Deprecated
  public Map<String, Object> getVariableCache() {
    return getCachedVariableMap();
  }


  /**
   * retrieves a local variable's value of type `T`. If the variable is not null and
   * its value is not null, it returns a casted instance of `T`. Otherwise, it returns
   * `null`.
   * 
   * @param variableName name of the variable to be retrieved as a typed value.
   * 
   * @returns a `T` object representing the value of the specified variable.
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
   * retrieves a `TypedValue` object associated with a given `variableName`, casting
   * it to the specified type `T` if found, otherwise returning `null`.
   * 
   * @param variableName name of the variable to be retrieved from the association
   * manager, which is then used to retrieve the typed value associated with that
   * variable name.
   * 
   * @returns a `TypedValue` object representing the local variable with the given name,
   * or `null` if the variable does not exist.
   */
  @SuppressWarnings("unchecked")
  public <T extends TypedValue> T getVariableLocalTyped(String variableName) {
    TypedValue variable = associationManager.getVariableLocal(variableName);
    return variable != null ? (T) variable : null;
  }


  /**
   * sets a variable locally within an association manager object.
   * 
   * @param variableName name of the variable to be set locally.
   * 
   * @param value local value of the variable that is being set.
   */
  public void setVariableLocal(String variableName, Object value) {
    associationManager.setVariableLocal(variableName, value);
  }


  public VariableMap getAndClearCachedLocalVariableMap() {
    VariableMap cachedVariablesLocal = associationManager.getCachedLocalVariables();
    VariableMap copy = new VariableMapImpl(cachedVariablesLocal);
    cachedVariablesLocal.clear();
    return copy;
  }


  @Deprecated
  public Map<String, Object> getAndClearVariableLocalCache() {
    return getAndClearCachedLocalVariableMap();
  }


  public VariableMap getCachedLocalVariableMap() {
    return new VariableMapImpl(associationManager.getCachedLocalVariables());
  }


  @Deprecated
  public Map<String, Object> getVariableLocalCache() {
    return getCachedLocalVariableMap();
  }


  public void flushVariableCache() {
    associationManager.flushVariableCache();
  }

  // ----------------------------------- Getters / Setters

  /*
   * Note that Producers should go into {@link CurrentProcessInstance} in
   * order to allow for specializing {@link BusinessProcess}.
   */


  /**
   * starts a task with the given ID.
   * 
   * @param task ID of an existing task to be started when the function is called.
   */
  public void setTask(Task task) {
    startTask(task.getId());
  }


  /**
   * sets the `taskId` parameter to the value passed as an argument, which then triggers
   * the start of the task associated with that ID.
   * 
   * @param taskId identification of a task to be executed by the `startTask()` method,
   * which is called by the `setTaskId()` function.
   */
  public void setTaskId(String taskId) {
    startTask(taskId);
  }


  /**
   * associates an execution with the given ID to the object instance.
   * 
   * @param execution Execution object that will be associated with the current code
   * generation task.
   */
  public void setExecution(Execution execution) {
    associateExecutionById(execution.getId());
  }


  /**
   * associates an execution ID with a code snippet, enabling its execution history to
   * be tracked and managed.
   * 
   * @param executionId unique identifier for an execution and is used to associate it
   * with the current code execution.
   */
  protected void setExecutionId(String executionId) {
    associateExecutionById(executionId);
  }


  public String getProcessInstanceId() {
    Execution execution = associationManager.getExecution();
    return execution != null ? execution.getProcessInstanceId() : null;
  }

  /**
   * Returns the id of the process associated with the current something or '0'.
   */
  public String getTaskId() {
    Task task = getTask();
    return task != null ? task.getId() : null;
  }

  public Task getTask() {
    return associationManager.getTask();
  }


  public Execution getExecution() {
    return associationManager.getExecution();
  }


  public String getExecutionId() {
    Execution e = getExecution();
    return e != null ? e.getId() : null;
  }


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

  protected void assertExecutionAssociated() {
    if (associationManager.getExecution() == null) {
      throw new ProcessEngineCdiException("No execution associated. Call busniessProcess.associateExecutionById() or businessProcess.startTask() first.");
    }
  }

  protected void assertTaskAssociated() {
    if (associationManager.getTask() == null) {
      throw new ProcessEngineCdiException("No task associated. Call businessProcess.startTask() first.");
    }
  }

  protected void assertCommandContextNotActive() {
    if(Context.getCommandContext() != null) {
      throw new ProcessEngineCdiException("Cannot use this method of the BusinessProcess bean from an active command context.");
    }
  }

}
