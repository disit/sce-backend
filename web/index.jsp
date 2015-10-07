<%-- 
    Document   : index
    Created on : 18-feb-2014, 15.36.04
    Author     : Daniele Cenni
    email      : daniele.cenni@unifi.it
--%>
<%@page import="org.quartz.TriggerKey"%>
<%@page import="org.quartz.TriggerBuilder"%>
<%@page import="org.quartz.Trigger"%>
<%@page import="org.quartz.JobKey"%>
<%@page import="org.quartz.SchedulerException"%>
<%@page import="org.json.simple.JSONObject"%>
<%@page import="org.json.simple.JSONArray"%>
<%@page import="org.json.simple.JSONValue"%>
<%@page import="org.json.simple.parser.JSONParser"%>
<%@page import="sce.*" %>
<%@page import="java.util.Map"%>
<%@page import="java.util.HashMap"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%
  Main m = Main.getInstance();
  JSONObject results_obj = new JSONObject();
  JSONArray legend_list = new JSONArray();
  
  //read json from request
  JSONParser parser = new JSONParser();
  Object obj = parser.parse(request.getParameter("json"));
  JSONObject jsonObject = (JSONObject) obj;
  
  // http://quartz-scheduler.org/api/2.2.0/org/quartz/Scheduler.html

  // TODO
  // addCalendar(String calName, Calendar calendar, boolean replace, boolean updateTriggers)
  // Add (register) the given Calendar to the Scheduler

  // TODO
  // addJob(JobDetail jobDetail, boolean replace)
  // Add the given Job to the Scheduler - with no associated Trigger

  // TODO
  // addJob(JobDetail jobDetail, boolean replace, boolean storeNonDurableWhileAwaitingScheduling)
  // Add the given Job to the Scheduler - with no associated Trigger

  // Determine whether a Job with the given identifier already exists within the scheduler
  if (jsonObject.get("id") != null && jsonObject.get("id").equals("checkExistJob")
          && jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
      legend_list.add("response");
      results_obj.put(0, legend_list);
      results_obj.put(1, (m.getScheduler().checkExists(JobKey.jobKey((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup"))) ? "true" : "false"));
      out.write(results_obj.toJSONString());
  } 
  // Determine whether a Trigger with the given identifier already exists within the scheduler
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("checkExistTrigger")
          && jsonObject.get("triggerName") != null && jsonObject.get("triggerGroup") != null) {
      legend_list.add("response");
      results_obj.put(0, legend_list);
      results_obj.put(1, (m.getScheduler().checkExists(TriggerKey.triggerKey((String)jsonObject.get("triggerName"), (String)jsonObject.get("triggerGroup"))) ? "true" : "false"));
      out.write(results_obj.toJSONString());
  } 
  // Clears (deletes!) all scheduling data - all Jobs, Triggers Calendars
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("clear")) {
    m.getScheduler().clear(); //method returns void
    out.write("<p>true</p>");
  } 
  // Delete the identified Calendar from the Scheduler
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("deleteCalendar") && jsonObject.get("calendarName") != null) {
    out.write("<p>" + m.getScheduler().deleteCalendar((String)jsonObject.get("calendarName")) + "</p>");
  } 
  // Delete the identified Job from the Scheduler and any associated Trigger [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("deleteJob")
          && jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.deleteJob((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup")));
    out.write(results_obj.toJSONString());
  }
  // Clears (deletes!) all scheduling data - all Jobs, Triggers Calendars [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("clearScheduler")) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.clear());
    out.write(results_obj.toJSONString());
  }
  
  // TODO
  // deleteJobs(List<JobKey> jobKeys)
  // Delete the identified Jobs from the Scheduler - and any associated Triggers
  
  // Get currently executing jobs [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getCurrentlyExecutingJobs")) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.getCurrentlyExecutingJobs());
    out.write(results_obj.toJSONString());
  }
  // Get triggers of job [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getTriggersOfJob")
          && jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.getTriggersOfJob((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup")));
    out.write(results_obj.toJSONString());
  }
  // Get the JobDetail for the Job instance with the given key
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getJobDetail")
          && jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    out.write("<p>" + m.getJobDetail((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup")) + "</p>");
  } 
  // Get the names of all known JobDetail groups
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getJobGroupNames")) {
    out.write("<p>" + m.getJobGroupNames() + "</p>");
  } 
  // Get the names of all Trigger groups that are paused
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getPausedTriggerGroups")) {
    out.write("<p>" + m.getPausedTriggerGroups() + "</p>");
  } 
  // Returns the instance Id of the Scheduler
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getSchedulerInstanceId")) {
    out.write("<p>" + m.getScheduler().getSchedulerInstanceId() + "</p>");
  } 
  // Returns the name of the Scheduler
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getSchedulerName")) {
    out.write("<p>" + m.getScheduler().getSchedulerName() + "</p>");
  } 
  // Get the names of all known trigger groups
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getTriggerGroupNames")) {
    out.write("<p>" + m.getTriggerGroupNames() + "</p>");
  }
  // Get the names of all the Triggers in the given group
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getTriggerKeys") && jsonObject.get("triggerGroup") != null) {
    out.write("<p>" + m.getTriggerKeys((String)jsonObject.get("triggerGroup")) + "</p>");
  }
  // Get all Triggers that are associated with the identified JobDetail
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getTriggersOfJob")
          && jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    out.write("<p>" + m.getTriggersOfJob((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup")) + "</p>");
  }
  // Get the current state of the identified Trigger
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getTriggerState")
          && jsonObject.get("triggerName") != null && jsonObject.get("triggerGroup") != null) {
    out.write("<p>" + m.getTriggerState((String)jsonObject.get("triggerName"), (String)jsonObject.get("triggerGroup")) + "</p>");
  }
  // Request the interruption, within this Scheduler instance, of all currently executing instances of the identified Job, which must be an implementor of the InterruptableJob interface [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("interruptJob")
          && jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.interruptJob((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup")));
    out.write(results_obj.toJSONString());
  }
  // Request the interruption, within this Scheduler instance, of the identified executing Job instance, which must be an implementor of the InterruptableJob interface
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("interruptJobInstance")
          && jsonObject.get("fireInstanceId") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.interruptJobInstance((String)jsonObject.get("fireInstanceId")));
    out.write(results_obj.toJSONString());
  }
  // Request the interruption, within this Scheduler instance, of all currently executing Jobs, which must be an implementor of the InterruptableJob interface [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("interruptJobs")) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.interruptJobs());
    out.write(results_obj.toJSONString());
  }
  // Request the interruption, within this Scheduler instance, of the identified executing Job instance, which must be an implementor of the InterruptableJob interface
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("interruptFireInstanceId")
          && jsonObject.get("fireInstanceId") != null) {
    out.write("<p>" + (m.getScheduler().interrupt((String)jsonObject.get("fireInstanceId")) ? "true" : "false") + "</p>");
  }
  // Reports whether the Scheduler is in stand-by mode
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("isInStandbyMode")) {
      legend_list.add("response");
      results_obj.put(0, legend_list);
      results_obj.put(1, (m.getScheduler().isInStandbyMode() ? "true" : "false"));
      out.write(results_obj.toJSONString());
  }
  // Reports whether the Scheduler has been shutdown
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("isShutdown")) {
      legend_list.add("response");
      results_obj.put(0, legend_list);
      results_obj.put(1, (m.getScheduler().isShutdown() ? "true" : "false"));
      out.write(results_obj.toJSONString());
  }
  // Whether the scheduler has been started
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("isStarted")) {
      legend_list.add("response");
      results_obj.put(0, legend_list);
      results_obj.put(1, (m.getScheduler().isStarted() ? "true" : "false"));
      out.write(results_obj.toJSONString());
  }
  // Pause all triggers - similar to calling pauseTriggerGroup(group) on every group, however, after using this method resumeAll() must be
  // called to clear the scheduler's state of 'remembering' that all new triggers will be paused as they are added
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("pauseAll")) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.pauseAll());
    out.write(results_obj.toJSONString());
  }
  // Pause the JobDetail with the given key by pausing all of its current Triggers [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("pauseJob")
          && jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.pauseJob((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup")));
    out.write(results_obj.toJSONString());
  }
  // Pause all of the JobDetails in the matching group by pausing all of their Triggers
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("pauseJobs")
          && jsonObject.get("groupName") != null) {
    out.write("<p>" + m.pauseJobs((String)jsonObject.get("groupName")) + "</p>");
  }
  // Pause the Trigger with the given key
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("pauseTrigger")
          && jsonObject.get("triggerName") != null && jsonObject.get("triggerGroup") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.pauseTrigger((String)jsonObject.get("triggerName"), (String)jsonObject.get("triggerGroup")));
    out.write(results_obj.toJSONString());
  }
  // Pause all of the Triggers in the matching group
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("pauseTriggers")
          && jsonObject.get("groupName") != null) {
    out.write("<p>" + m.pauseTriggers((String)jsonObject.get("groupName")) + "</p>");
  }
  
  // TODO
  // rescheduleJob(TriggerKey triggerKey, Trigger newTrigger)
  // Remove (delete) the Trigger with the given key, and store the
  // new given one - which must be associated with the same job (the
  // new trigger must have the job name & group specified) - however, the
  // new trigger need not have the same name as the old trigger.
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("rescheduleJob")
          && jsonObject.get("withIdentityNameGroup") != null) {
    //out.write("<p>" + m.rescheduleJob(jsonObject.getMap(), (jsonObject.get("withIdentityNameGroup").split("\\."))[0], (jsonObject.get("withIdentityNameGroup").split("\\."))[1]) + "</p>");
    JSONArray  array = (JSONArray) jsonObject.get("withIdentityNameGroup");
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.rescheduleJob(jsonObject, (String) array.get(0), (String) array.get(1)));
    out.write(results_obj.toJSONString());
  }
  
  // Resume (un-pause) all triggers - similar to calling resumeTriggerGroup (group) on every group
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("resumeAll")) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.resumeAll());
    out.write(results_obj.toJSONString());
  }
  // Resume (un-pause) the JobDetail with the given key
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("resumeJob")
          && jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.resumeJob((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup")));
    out.write(results_obj.toJSONString());
  }
  // Resume (un-pause) all of the JobDetails in matching group
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("resumeJobs")
          && jsonObject.get("groupName") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.resumeJobs((String)jsonObject.get("groupName")));
    out.write(results_obj.toJSONString());
  }
  // Resume (un-pause) the Trigger with the given key
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("resumeTrigger")
          && jsonObject.get("triggerName") != null && jsonObject.get("triggerGroup") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.resumeTrigger((String)jsonObject.get("triggerName"), (String)jsonObject.get("triggerGroup")));
    out.write(results_obj.toJSONString());
  }
  // Resume (un-pause) all of the Triggers in matching group
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("resumeTriggers")
          && jsonObject.get("groupName") != null) {
    out.write("<p>" + m.resumeTriggers((String)jsonObject.get("groupName")) + "</p>");
  }
  
  // TODO 
  // scheduleJob(JobDetail jobDetail, Set<? extends Trigger> triggersForJob, boolean replace)
  // Schedule the given job with the related set of triggers.
  
  // TODO
  // scheduleJob(JobDetail jobDetail, Trigger trigger)
  // Add the given JobDetail to the Scheduler, and associate the given Trigger with it.
  
  // TODO
  // ScheduleJob(Trigger trigger)
  // Schedule the given Trigger with the Job identified by the Trigger's settings.
 
  // TODO
  // scheduleJobs(Map<JobDetail,Set<? extends Trigger>> triggersAndJobs, boolean replace)
  // Schedule all of the given jobs with the related set of triggers.
 
  // TODO 
  // setJobFactory(JobFactory factory)
  // Set the JobFactory that will be responsible for producing instances of Job classes.  
  
  // Halts the Scheduler's firing of Triggers, and cleans up all resources associated with the Scheduler
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("shutdownScheduler") 
          && jsonObject.get("waitForJobsToComplete") != null) {
    boolean waitForJobsToComplete = true;
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.shutdownScheduler((jsonObject.get("waitForJobsToComplete").equals("true")) ? waitForJobsToComplete : !waitForJobsToComplete));
    out.write(results_obj.toJSONString());
  }
  
  // Temporarily halts the Scheduler's firing of Triggers [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("standbyScheduler")) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.standbyScheduler());
    out.write(results_obj.toJSONString());
  }
  
  // Starts the Scheduler's threads that fire Triggers [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("startScheduler")) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.startScheduler());
    out.write(results_obj.toJSONString());
  }
  
  // Calls {#start()} after the indicated number of seconds.
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("startDelayed")
          && jsonObject.get("seconds") != null) {
    m.getScheduler().startDelayed(Integer.parseInt((String)jsonObject.get("seconds"))); //method returns void
    out.write("<p>true</p>");
  }
  
  // Truncate catalina.out (Tomcat log file)
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("truncateCatalinaLog")) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.truncateCatalinaLog());
    out.write(results_obj.toJSONString());
  }

  // Trigger the identified JobDetail (execute it now) [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("triggerJob")
          && jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.triggerJob((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup")));
    out.write(results_obj.toJSONString());
  }
  
  // TODO 
  // triggerJob(JobKey jobKey, JobDataMap data)
  // Trigger the identified JobDetail (execute it now).
  
  // Remove the indicated Trigger from the scheduler.
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("unscheduleJob") &&
          jsonObject.get("triggerName") != null && jsonObject.get("triggerGroup") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.unscheduleJob((String)jsonObject.get("triggerName"), (String)jsonObject.get("triggerGroup")));
    out.write(results_obj.toJSONString());
  }
  
  // TODO 
  // unscheduleJobs(List<TriggerKey> triggerKeys)
  // Remove all of the indicated Triggers from the scheduler.
  
  // schedule job with a dumb job and trigger [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("scheduleJob1")) {
    out.write("<p>" + m.scheduleJob1(DumbJob.class) + "</p>");
  } 
  // list jobs [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("listJobs")) {
    out.write("<p>" + m.listJobs() + "</p>");
  }
  // list job's triggers [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("listJobTriggers")) {
    out.write("<p>" + m.listJobTriggers(JobKey.jobKey((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup"))) + "</p>");
  }
  // schedule job with a new trigger [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("jobClass") != null && jsonObject.get("id").equals("scheduleJob")) {
    //out.write("<p>" + m.scheduleJob(jsonObject.getMap()) + "</p>");
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.scheduleJob(jsonObject));
    out.write(results_obj.toJSONString());
  }
  // update job with a new job [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("jobClass") != null && jsonObject.get("id").equals("updateJob")) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.updateJob(jsonObject));
    out.write(results_obj.toJSONString());
  }
  // update job with a json [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("updateJobDataMap") 
          && jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null
          && jsonObject.get("jobDataMap")!= null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.updateJobDataMap(jsonObject));
    out.write(results_obj.toJSONString());
  }
  // add the given Job to the Scheduler - with no associated Trigger. The Job will be 'dormant' until it is scheduled with a Trigger, or Scheduler.triggerJob() is called for it.
  // With the jsonObject.get("storeDurably") parameter set to true (default), a non-durable job can be stored. Once it is scheduled, it will resume normal non-durable behavior 
  // (i.e. be deleted once there are no remaining associated triggers). [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("jobClass") != null && jsonObject.get("id").equals("addJob")) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.addJob(jsonObject));
    out.write(results_obj.toJSONString());
  }
  // get the notification email eventually set in the job data map [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getNotificationEmail") &&
          jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    legend_list.add("response");
    results_obj.put(0, legend_list);
    results_obj.put(1, m.getNotificationEmail((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup")));
    out.write(results_obj.toJSONString());
  }
  // get the job data map as a json [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getJobDataMap") &&
          jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    out.write(m.getJobDataMap((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup"))); //json string
  }
  // get the job fire times as a json [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getJobFireTimes") &&
          jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    out.write(m.getJobFireTimes((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup"))); //json string
  }
  // get the list of triggers associated to a job as a json [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getJobTriggers") &&
          jsonObject.get("jobName") != null && jsonObject.get("jobGroup") != null) {
    out.write(m.getJobTriggers((String)jsonObject.get("jobName"), (String)jsonObject.get("jobGroup"))); //json string
  }
  //get scheduler metadata as a json [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getSchedulerMetadata")) {
    out.write(m.getSchedulerMetadata()); //json string
  }
  //get the system status as a json [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getSystemStatus")) {
    out.write(m.getSystemStatus()); //json string
  }
  //get connection pool info as a json [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getConnectionPoolInfo")) {
    out.write(m.getConnectionPoolInfo()); //json string
  }
  // build trigger for existing job [implemented]
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("buildTriggerForJob")) {
      try {
          Map<String, String[]> map = new HashMap();
          map.putAll(jsonObject);
          TriggerBuilder triggerBuilder = m.buildTrigger(jsonObject);
          Trigger trigger = triggerBuilder.build();
          out.write("<p>" + m.getScheduler().scheduleJob(trigger).toString() + "</p>");
      } catch (SchedulerException e) {
          out.write("<p>" + e.getMessage() + "</p>");
      }
  }
  // set job progress
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("setJobProgress")
          && jsonObject.get("fire_instance_id") != null && jsonObject.get("progress") != null) {
      out.write("<p>" + m.setJobProgress((String)jsonObject.get("fire_instance_id"), Double.parseDouble((String)jsonObject.get("progress"))) + "</p>");
  }
  // get job progress
  else if (jsonObject.get("id") != null && jsonObject.get("id").equals("getJobProgress")
          && jsonObject.get("fire_instance_id") != null) {
      out.write("<p>" + m.getJobProgress((String)jsonObject.get("fire_instance_id")) + "</p>");
  } 
  //ERROR
  else {
    out.write("<p>unknown function called or missing parameters</p>");
  }
/*<!DOCTYPE html>
<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Smart Cloud Engine</title>
  </head>
  <body>
  </body>
</html>*/
%>
