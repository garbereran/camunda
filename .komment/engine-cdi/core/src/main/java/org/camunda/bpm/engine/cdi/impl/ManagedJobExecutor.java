{"name":"ManagedJobExecutor.java","path":"engine-cdi/core/src/main/java/org/camunda/bpm/engine/cdi/impl/ManagedJobExecutor.java","content":{"structured":{"description":"","items":[{"id":"413ffaf6-58aa-4442-b571-a6275e074032","ancestors":[],"type":"function","name":"executeJobs","location":{"offset":" ","indent":2,"insert":43,"start":43},"returns":false,"params":[{"name":"jobIds","type":"List<String>"},{"name":"processEngine","type":"ProcessEngineImpl"}],"code":"@Override\n  public void executeJobs(List<String> jobIds, ProcessEngineImpl processEngine) {\n    try {\n      managedExecutorService.execute(getExecuteJobsRunnable(jobIds, processEngine));\n    } catch (RejectedExecutionException e) {\n      logRejectedExecution(processEngine, jobIds.size());\n      rejectedJobsHandler.jobsRejected(jobIds, processEngine, this);\n    }\n  }","skip":false,"length":9,"comment":{"description":"","params":[{"name":"jobIds","type":"List<String>","description":""},{"name":"processEngine","type":"ProcessEngineImpl","description":""}],"returns":null}},{"id":"cdb2283d-1bb8-4686-8d64-cb7a0434e1ba","ancestors":[],"type":"function","name":"startExecutingJobs","location":{"offset":" ","indent":2,"insert":53,"start":53},"returns":false,"params":[],"code":"@Override\n  protected void startExecutingJobs() {\n    try {\n      managedExecutorService.execute(acquireJobsRunnable);\n    } catch (Exception e) {\n      throw new ProcessEngineException(\"Could not schedule AcquireJobsRunnable for execution.\", e);\n    }\n  }","skip":false,"length":8,"comment":{"description":"","params":[],"returns":null}},{"id":"6a6465fd-096c-4d27-a304-8f002cb9efe9","ancestors":[],"type":"function","name":"stopExecutingJobs","location":{"offset":" ","indent":2,"insert":62,"start":62},"returns":false,"params":[],"code":"@Override\n  protected void stopExecutingJobs() {\n    // nothing to do\n  }","skip":false,"length":4,"comment":{"description":"","params":[],"returns":null}}]}}}