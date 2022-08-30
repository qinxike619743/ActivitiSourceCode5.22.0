/* Licensed under the Apache License, Version 2.0 (the "License");
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

package org.activiti.engine.impl.context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Stack;

import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.jobexecutor.JobExecutorContext;
import org.activiti.engine.impl.persistence.deploy.ProcessDefinitionInfoCacheObject;
import org.activiti.engine.impl.pvm.runtime.InterpretableExecution;

import com.fasterxml.jackson.databind.node.ObjectNode;


/**
 * @author Tom Baeyens
 * @author Daniel Meyer
 */
public class Context {
/*
* 为了避免线程冲突, 每一个命令都会在一个独立的命令上下文中执行
* */
  protected static ThreadLocal<Stack<CommandContext>> commandContextThreadLocal = new ThreadLocal<Stack<CommandContext>>();
  protected static ThreadLocal<Stack<ProcessEngineConfigurationImpl>> processEngineConfigurationStackThreadLocal = new ThreadLocal<Stack<ProcessEngineConfigurationImpl>>();
  protected static ThreadLocal<Stack<ExecutionContext>> executionContextStackThreadLocal = new ThreadLocal<Stack<ExecutionContext>>();
  protected static ThreadLocal<JobExecutorContext> jobExecutorContextThreadLocal = new ThreadLocal<JobExecutorContext>();
  /*
  ThreadLocal 是为了解决线程安全问题 解决的, 该类内部维护了一个Map集合
  用于存储每一个线程变量的副本, Map的key为当前线程对象, value为对应的线程变量副本
  由于key 是不能重复的 从而达到了线程安全的目的
   */
  protected static ThreadLocal<Map<String, ObjectNode>> bpmnOverrideContextThreadLocal = new ThreadLocal<Map<String, ObjectNode>>();
  protected static ResourceBundle.Control resourceBundleControl = new ResourceBundleControl();
  /*
  * 该() 首先调用 getStack () 从 当前线程中 获取命令上下文, 然后再获取栈顶的元素值
  * ThreladoLocal 为什么被限制为 Stack类型 而不是Map 或者 其他集合类型呢 ??
  * 因为 程序首先创建命令上下文实例对象, 然后 设置下一个需要执行的命令拦截器 最后一处命令上下文实例对象
  * 而创建和移除命令上下文 的过程 刚好 和  入栈  和出栈的原理类似
  * */
  public static CommandContext getCommandContext() {//获取命令上下文 实例对象

    Stack<CommandContext> stack = getStack(commandContextThreadLocal);
    //如果栈为空 则返回 空
    if (stack.isEmpty()) {
      return null;
    }
    return stack.peek();//获取栈顶的元素
  }

  public static void setCommandContext(CommandContext commandContext) {
    getStack(commandContextThreadLocal).push(commandContext);
  }

  public static void removeCommandContext() {
    getStack(commandContextThreadLocal).pop();
  }

  public static ProcessEngineConfigurationImpl getProcessEngineConfiguration() {
    Stack<ProcessEngineConfigurationImpl> stack = getStack(processEngineConfigurationStackThreadLocal);
    if (stack.isEmpty()) {
      return null;
    }
    return stack.peek();
  }

  public static void setProcessEngineConfiguration(ProcessEngineConfigurationImpl processEngineConfiguration) {
    getStack(processEngineConfigurationStackThreadLocal).push(processEngineConfiguration);
  }

  public static void removeProcessEngineConfiguration() {
    getStack(processEngineConfigurationStackThreadLocal).pop();
  }

  public static ExecutionContext getExecutionContext() {
    return getStack(executionContextStackThreadLocal).peek();
  }
  
  public static boolean isExecutionContextActive() {
  	Stack<ExecutionContext> stack = executionContextStackThreadLocal.get();
  	return stack != null && !stack.isEmpty();
  }

  public static void setExecutionContext(InterpretableExecution execution) {
    getStack(executionContextStackThreadLocal).push(new ExecutionContext(execution));
  }

  public static void removeExecutionContext() {
    getStack(executionContextStackThreadLocal).pop();
  }

  protected static <T> Stack<T> getStack(ThreadLocal<Stack<T>> threadLocal) {
    Stack<T> stack = threadLocal.get(); //首先从集合中获取
    if (stack==null) {//如果当前线程不存在元素
      stack = new Stack<T>();//实例化栈 并将其设置到threadLocal集合中
      threadLocal.set(stack);
    }
    return stack;
  }
  
  public static JobExecutorContext getJobExecutorContext() {
    return jobExecutorContextThreadLocal.get();
  }
  
  public static void setJobExecutorContext(JobExecutorContext jobExecutorContext) {
    jobExecutorContextThreadLocal.set(jobExecutorContext);
  }
  
  public static void removeJobExecutorContext() {
    jobExecutorContextThreadLocal.remove();
  }
  /*
  根据 任务节点的ID 值 和 processDefinitionId 的值从缓存中获取数据
   */
  public static ObjectNode getBpmnOverrideElementProperties(String id, String processDefinitionId) {
    //
    ObjectNode definitionInfoNode = getProcessDefinitionInfoNode(processDefinitionId);
    ObjectNode elementProperties = null;
    //如果 不为空
    if (definitionInfoNode != null) {
      //调用getBpmnElementProperties() 获取当前任务节点的缓存数据
      elementProperties = getProcessEngineConfiguration().getDynamicBpmnService().getBpmnElementProperties(id, definitionInfoNode);
    }
    return elementProperties;
  }
  
  public static ObjectNode getLocalizationElementProperties(String language, String id, String processDefinitionId, boolean useFallback) {
    ObjectNode definitionInfoNode = getProcessDefinitionInfoNode(processDefinitionId);
    ObjectNode localizationProperties = null;
    if (definitionInfoNode != null) {
      if (useFallback == false) {
        localizationProperties = getProcessEngineConfiguration().getDynamicBpmnService().getLocalizationElementProperties(
            language, id, definitionInfoNode);
        
      } else {
        HashSet<Locale> candidateLocales = new LinkedHashSet<Locale>();
        candidateLocales.addAll(resourceBundleControl.getCandidateLocales(id, new Locale(language)));
        candidateLocales.addAll(resourceBundleControl.getCandidateLocales(id, Locale.getDefault()));
        for (Locale locale : candidateLocales) {
          localizationProperties = getProcessEngineConfiguration().getDynamicBpmnService().getLocalizationElementProperties(
              locale.getLanguage(), id, definitionInfoNode);
          
          if (localizationProperties != null) {
            break;
          }
        }
      }
    }
    return localizationProperties;
  }
  
  public static void removeBpmnOverrideContext() {
    bpmnOverrideContextThreadLocal.remove();
  }
  /*

   */
  protected static ObjectNode getProcessDefinitionInfoNode(String processDefinitionId) {
    //调用 getBpmnOverrideContext() 主要是为了确保  Context类中的 bpmnOverrideCntextThreadLocal 变量已经被初始化
    Map<String, ObjectNode> bpmnOverrideMap = getBpmnOverrideContext();
    //如果 processDefinitionID 不存在  bpmnOverrideMap集合中
    if (bpmnOverrideMap.containsKey(processDefinitionId) == false) {
      //从缓存中查找该processDefinitionId 对应的节点缓存数据
      ProcessDefinitionInfoCacheObject cacheObject = getProcessEngineConfiguration().getDeploymentManager()
          .getProcessDefinitionInfoCache()
          .get(processDefinitionId);
      
      addBpmnOverrideElement(processDefinitionId, cacheObject.getInfoNode());
    }
    
    return getBpmnOverrideContext().get(processDefinitionId);
  }
  
  protected static Map<String, ObjectNode> getBpmnOverrideContext() {
    Map<String, ObjectNode> bpmnOverrideMap = bpmnOverrideContextThreadLocal.get();
    if (bpmnOverrideMap == null) {
      bpmnOverrideMap = new HashMap<String, ObjectNode>();
    }
    return bpmnOverrideMap;
  }
  
  protected static void addBpmnOverrideElement(String id, ObjectNode infoNode) {
    Map<String, ObjectNode> bpmnOverrideMap = bpmnOverrideContextThreadLocal.get();
    if (bpmnOverrideMap == null) {
      bpmnOverrideMap = new HashMap<String, ObjectNode>();
      bpmnOverrideContextThreadLocal.set(bpmnOverrideMap);
    }
    bpmnOverrideMap.put(id, infoNode);
  }
  
  static class ResourceBundleControl extends ResourceBundle.Control {
    @Override
    public List<Locale> getCandidateLocales(String baseName, Locale locale) {
      return super.getCandidateLocales(baseName, locale);
    }
  }
}
