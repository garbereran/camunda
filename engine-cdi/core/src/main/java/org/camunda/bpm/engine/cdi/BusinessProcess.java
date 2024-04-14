
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
   * starts a process instance based on a given process definition ID, retrieves the
   * latest state from the process engine, and sets the execution variable to reference
   * the started instance.
   * 
   * @param processDefinitionId identifier of the process definition to start.
   * 
   * @returns a `ProcessInstance` object representing the started process.
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
   * and sets the execution object if not.
   * 
   * @param processDefinitionId identity of the process definition to start.
   * 
   * @param businessKey business key of the process instance to be started, which is
   * used to identify the process instance in the process engine.
   * 
   * @returns a `ProcessInstance` object containing the started process instance details.
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
   * starts a process instance by id, updating the variables map and then calling the
   * `startProcessInstanceById` method of the process engine to initiate the process.
   * 
   * @param processDefinitionId ID of the process definition to start.
   * 
   * @param variables map of variables to be passed as arguments to the started process
   * instance, which are then made available to the process instance through the
   * `cachedVariables` variable map.
   * 
   * @returns a `ProcessInstance` object representing the started process.
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
   * starts a process instance with the specified ID, business key, and variables. It
   * retrieves the cached variables, updates them with the provided ones, and then
   * starts the process instance using the `getRuntimeService().startProcessInstanceById()`
   * method. If the instance is not ended, it sets the execution to the newly started
   * instance.
   * 
   * @param processDefinitionId identifier of the process definition to start.
   * 
   * @param businessKey unique identifier of the business process instance to be started,
   * which is used by the `processEngine.getRuntimeService().startProcessInstanceById()`
   * method to locate the correct process definition and start the instance.
   * 
   * @param variables map of variables to be used when starting the process instance,
   * which is added to the existing cached variables and then passed to the
   * `startProcessInstanceById()` method.
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
   * starts a process instance by its key in the process engine, retrieves the processed
   * instance if it is not ended, sets the execution to the instance, and returns the
   * instance.
   * 
   * @param key unique identifier of the process instance to be started.
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
   * starts a process instance by key and business key, checks if it is ended, sets
   * execution to the started instance and returns it.
   * 
   * @param key process instance key to be started.
   * 
   * @param businessKey business key of the process instance to be started, which is
   * used to identify the specific process instance to be activated by the `startProcessByKey()`
   * method.
   * 
   * @returns a `ProcessInstance` object representing the started process.
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
   * starts a process instance by its key and passes variables to it. It first checks
   * if the command context is active, then gets and clears the cached variable map,
   * puts all the given variables into it, starts the process instance using the
   * `startProcessInstanceByKey` method of the process engine, and sets the execution
   * of the instance if it's not ended.
   * 
   * @param key identifier of the process instance to start.
   * 
   * @param variables map of variables to be passed to the started process instance,
   * which is stored in the `cachedVariables` map and used to initialize the process instance.
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
   * starts a process instance based on its key and associated business key, and passes
   * variables to the start process command.
   * 
   * @param key unique process instance key to be started.
   * 
   * @param businessKey business key of the process instance to be started, which is
   * used by the `startProcessInstanceByKey` method to identify the correct process
   * instance to start.
   * 
   * @param variables map of variables to be used when starting a process instance by
   * its key, and it is passed to the `processEngine.getRuntimeService().startProcessInstanceByKey()`
   * method for injection into the process instance.
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
   * starts a process instance by sending a message to the process engine, retrieves
   * the created instance, and sets its execution status.
   * 
   * @param messageName name of the message to start the process instance with.
   * 
   * @returns a `ProcessInstance` object representing the started process.
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
   * starts a process instance by sending a message to the process engine, passing in
   * the name of the message and any necessary variable values.
   * 
   * @param messageName name of the message that initiates the process instance, which
   * is passed to the `startProcessInstanceByMessage()` method of the Process Engine
   * to start a new process instance based on the specified message.
   * 
   * @param processVariables variables that will be used by the process instance when
   * it is started, and gets added to the cache of variable map before starting the
   * process instance.
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
   * starts a process instance based on a message name and business key, passing variable
   * maps as parameters to the process engine's `startProcessInstanceByMessage` method.
   * It retrieves and clears the cached variables before starting the instance and sets
   * the execution status accordingly.
   * 
   * @param messageName name of the message to start the process instance.
   * 
   * @param businessKey unique identifier for a specific business process, which is
   * used to identify the correct process instance to start when calling the
   * `startProcessByMessage()` method.
   * 
   * @param processVariables variables to be passed to the started process instance,
   * which are added to the existing variable map of the current execution context
   * before starting the process.
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
   * associates an execution with a given ID to a process instance. It retrieves the
   * execution from the repository based on the ID, and sets the execution object to
   * the association manager for further processing.
   * 
   * @param executionId identifier of an execution to be associated with the given `associationManager`.
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
   * is used to indicate that a conversation should end or continue. When `endConversation`
   * is true, the function ends the conversation instance, otherwise it continues the
   * conversation.
   * 
   * @param endConversation conclusion of an active conversation with the AI assistant,
   * as it triggers the termination of the conversation instance.
   */
  public void signalExecution(boolean endConversation) {
    signalExecution();
    if(endConversation) {
      conversationInstance.get().end();
    }
  }

  // -------------------------------------

  /**
   * retrieves a task based on its ID and sets it as the current task in the association
   * manager. If the task is not found, an exception is thrown. The function also
   * associates the execution with the task's ID.
   * 
   * @param taskId id of the task to be resumed.
   * 
   * @returns a reference to the task with the specified ID, or an exception if the
   * task does not exist.
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
   * starts a task with the given ID, and if the parameter `beginConversation` is true,
   * it also begins a conversation if one does not already exist.
   * 
   * @param taskId id of the task to be started.
   * 
   * @param beginConversation initialization of a conversation instance, which determines
   * whether or not the conversation should be initiated when the task is started.
   * 
   * @returns a task handle that can be used to query the status of the task.
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
   * completes a task and ends a conversation, if necessary.
   * 
   * @param endConversation conclusion of an ongoing conversation, when set to true it
   * ends the conversation instance.
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
   * stops a task and ends a conversation if necessary.
   * 
   * @param endConversation conclusion of the conversation, prompting the instance of
   * the conversation to end when set to `true`.
   */
  public void stopTask(boolean endConversation) {
    stopTask();
    if(endConversation) {
      conversationInstance.get().end();
    }
  }

  // -------------------------------------------------


  /**
   * retrieves a variable's value of type T from an activity's variables. If the variable
   * is null, it returns null.
   * 
   * @param variableName name of a variable to be retrieved.
   * 
   * @returns a non-null object of type `T`, or `null` if no such variable exists.
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
   * retrieves a typed value associated with a given variable name, returning the typed
   * value as a generic type T if found, or null otherwise.
   * 
   * @param variableName name of a variable that is being retrieved from the association
   * manager.
   * 
   * @returns a typed value of the specified variable name, or `null` if the variable
   * is not found.
   */
  @SuppressWarnings("unchecked")
  public <T extends TypedValue> T getVariableTyped(String variableName) {
    TypedValue variable = associationManager.getVariable(variableName);
    return variable != null ? (T) (variable) : null;
  }


  /**
   * sets a variable in an association manager based on a given name and value.
   * 
   * @param variableName name of the variable to be set.
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
   * retrieves the value of a variable with the given name from the local storage, and
   * returns it as a specified type (T). If the variable is null or its value is null,
   * it returns null.
   * 
   * @param variableName name of a variable to be retrieved from the local scope, and
   * it is used to retrieve the corresponding typed value from the `getVariableLocalTyped()`
   * method.
   * 
   * @returns a non-null `T` object representing the value of the local variable with
   * the given name, or `null` if the variable is not found.
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
   * retrieves a TypedValue object associated with a given variable name, using the
   * Association Manager's local storage. If the variable is found, it returns the
   * object as a specified type (T), otherwise it returns null.
   * 
   * @param variableName name of the variable to be retrieved from the association manager.
   * 
   * @returns a `T` object representing the specified variable or `null` if the variable
   * is not found.
   */
  @SuppressWarnings("unchecked")
  public <T extends TypedValue> T getVariableLocalTyped(String variableName) {
    TypedValue variable = associationManager.getVariableLocal(variableName);
    return variable != null ? (T) variable : null;
  }


  /**
   * sets a local variable associated with an instance of an AssociationManager.
   * 
   * @param variableName name of a variable that will be assigned the value provided
   * by the `value` parameter.
   * 
   * @param value object that will be stored as the value of the specified variable in
   * the local association manager.
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
   * sets the value of a Task object to an id.
   * 
   * @param task Id of an existing task to be started in the function `startTask()`
   * when the `setTask()` method is called.
   */
  public void setTask(Task task) {
    startTask(task.getId());
  }


  /**
   * sets the value of the `taskId` field to a given `String` argument, starting a new
   * task with that ID.
   * 
   * @param taskId identity of a task to be initiated by calling the `startTask` method.
   */
  public void setTaskId(String taskId) {
    startTask(taskId);
  }


  /**
   * associates an execution with a unique identifier, stored in the `id` field.
   * 
   * @param execution execution to be associated with the current code document, as
   * indicated by the `associateExecutionById()` method call.
   */
  public void setExecution(Execution execution) {
    associateExecutionById(execution.getId());
  }


  /**
   * associates an execution ID with a specific code instance.
   * 
   * @param executionId unique identifier for the execution being associated with the
   * current instance of the code, which allows for proper association and tracking of
   * the execution within the system.
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
