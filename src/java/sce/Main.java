/* Smart Cloud/City Engine backend (SCE).
   Copyright (C) 2015 DISIT Lab http://www.disit.org - University of Florence

   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation; either version 2
   of the License, or (at your option) any later version.
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA. */

package sce;

//Use static imports for the matcher and key classes, to make the matchers cleaner:
/*import static org.quartz.JobKey.*;
 import static org.quartz.impl.matchers.KeyMatcher.*;
 import static org.quartz.impl.matchers.GroupMatcher.*;
 import static org.quartz.impl.matchers.AndMatcher.*;
 import static org.quartz.impl.matchers.OrMatcher.*;
 import static org.quartz.impl.matchers.EverythingMatcher.*;*/
import static org.quartz.JobBuilder.*;
import static org.quartz.TriggerBuilder.*;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.TriggerKey;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.TriggerBuilder;
import org.quartz.Trigger;
import org.quartz.Trigger.TriggerState;
import org.quartz.JobBuilder;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import org.quartz.SchedulerMetaData;
import org.quartz.xml.XMLSchedulingDataProcessor;
import org.quartz.simpl.CascadingClassLoadHelper;
import org.quartz.impl.StdSchedulerFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.text.ParseException;
import java.io.FileNotFoundException;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.json.simple.parser.JSONParser;

public class Main {

    private static Main instance; //private to ensure having only a singleton instance
    private static SchedulerFactory schedFact;
    private static Scheduler sched;
    private static ScheduledExecutorService scheduledThreadPool;
    private static Properties prop;
    private static ConnectionPool connectionPool;
    private static DataSource dataSource;

    private Main() {
        try {
            //load quartz.properties from src folder of this project
            prop = new Properties();
            prop.load(this.getClass().getResourceAsStream("quartz.properties"));

            //System.out.println(new java.io.File(".").getAbsolutePath());
            //create a new scheduler factory
            //schedFact = new StdSchedulerFactory();
            schedFact = new StdSchedulerFactory(prop);
            //schedFact = new StdSchedulerFactory("quartz.properties");

            //get the scheduler
            sched = schedFact.getScheduler();

            //start the scheduler
            sched.start();

            //start logging this scheduler status to db every n seconds, read from quartz.properties (schedulerLoggingPeriod)
            //Logging is stopped if the scheduler is shutdown with the shutdownScheduler method)
            scheduledThreadPool = Executors.newScheduledThreadPool(1);
            SchedulerLoggerStatus logger = new SchedulerLoggerStatus(sched, prop.getProperty("org.quartz.dataSource.quartzDataSource.URL"), prop.getProperty("org.quartz.dataSource.quartzDataSource.user"), prop.getProperty("org.quartz.dataSource.quartzDataSource.password"));
            scheduledThreadPool.scheduleAtFixedRate(logger, 1, Integer.parseInt(prop.getProperty("schedulerLoggingPeriod")), TimeUnit.SECONDS);

        } catch (SchedulerException | IOException e) {
        }
    }

    /* Schedule a new job */
    public String scheduleJob1(Class c) {
        try {
            // define the job and tie it to our DumbJob class
            JobDetail job = newJob(c)
                    //.withIdentity("myJob", "group1") // name "myJob", group "group1"
                    .withIdentity(String.valueOf(System.currentTimeMillis()), "job") //UUID = java.util.UUID.randomUUID().toString()
                    .withDescription("prova")
                    .usingJobData("jobSays", "Hello World!")
                    .usingJobData("myFloatValue", 3.141f)
                    .storeDurably(true) // the job is not removed from the JobStore when all triggers have completed
                    .build();

            //store the job description
            //result = job.getDescription();
            // Trigger the job to run now, and then every 40 seconds
            Trigger trigger = newTrigger()
                    .withIdentity(String.valueOf(System.currentTimeMillis()), "trigger") //UUID = java.util.UUID.randomUUID().toString()
                    .startNow() //.startAt(futureDate(10, MINUTES)
                    //.withPriority(10)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule() //.withSchedule(dailyAtHourAndMinute(9, 30))
                            .withIntervalInSeconds(40)
                            .repeatForever()) //.withRepeatCount(3)
                    .build();

            // Tell quartz to schedule the job using our trigger
            Date date = sched.scheduleJob(job, trigger);
            return date.toString();

            //Adding a SchedulerListener
            //SimpleSchedulerListener ssl = new SimpleSchedulerListener();
            //sched.getListenerManager().addSchedulerListener(ssl);
            //Adding a TriggerListener
            //SimpleTriggerListener stl = new SimpleTriggerListener();
            //sched.getListenerManager().addTriggerListener(stl);
            //Adding a JobListener
            //SimpleJobListener sjl = new SimpleJobListener();
            //sched.getListenerManager().addJobListener(sjl);
            //Adding a JobListener that is interested in a particular job
            //SimpleJobListener sjl = new SimpleJobListener();
            //sched.getListenerManager().addJobListener(sjl, KeyMatcher.keyEquals(new JobKey("myJobName", "myJobGroup")));
            //Adding a JobListener that is interested in all jobs of a particular group
            //SimpleJobListener sjl = new SimpleJobListener();
            //sched.getListenerManager().addJobListener(sjl, GroupMatcher.jobGroupEquals("myJobGroup"));
            //Adding a JobListener that is interested in all jobs of two particular groups
            //SimpleJobListener sjl = new SimpleJobListener();
            //sched.getListenerManager().addJobListener(sjl, OrMatcher.or(GroupMatcher.jobGroupEquals("myJobGroup"), GroupMatcher.jobGroupEquals("yourGroup")));
            //Adding a JobListener that is interested in all jobs
            //SimpleJobListener sjl = new SimpleJobListener();
            //sched.getListenerManager().addJobListener(sjl, EverythingMatcher.allJobs());
            // Add the the job to the scheduler's store
            //sched.addJob(job, false);
            //delete the job
            //System.out.println("job id: " + job.getKey());
            //deleteJob(job);
        } catch (ObjectAlreadyExistsException e) {
            return e.getMessage();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Delete a job and Unscheduling All of Its triggers
     * @param job the job to be deleted
     * @return true in case of success, error message otherwise
     */
    public String deleteJob(String jobName, String jobGroup) {
        boolean result;
        try {
            result = sched.deleteJob(JobKey.jobKey(jobName, jobGroup));
            return result ? "true" : "false";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Clears (deletes!) all scheduling data - all Jobs, Triggers Calendars.
     * @return true in case of success, error message otherwise
     */
    public String clear() {
        try {
            sched.clear();
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    //Request the interruption, within this Scheduler instance, 
    //of all currently executing instances of the identified Job, 
    //which must be an implementor of the InterruptableJob interface
    public String interruptJob(String jobName, String jobGroup) {
        boolean result;
        try {
            result = sched.interrupt(JobKey.jobKey(jobName, jobGroup));
            return result ? "true" : "false";
        } catch (UnableToInterruptJobException e) {
            return e.getMessage();
        }
    }

    //Request the interruption, within this Scheduler instance,
    //of the identified executing Job instance, which must be an
    //implementor of the InterruptableJob interface
    public String interruptJobInstance(String fireInstanceId) {
        boolean result;
        try {
            result = sched.interrupt(fireInstanceId);
            return result ? "true" : "false";
        } catch (UnableToInterruptJobException e) {
            return e.getMessage();
        }
    }

    //Request the interruption, within this Scheduler instance, 
    //of all currently executing Jobs, 
    //which must be an implementor of the InterruptableJob interface
    public String interruptJobs() {
        try {
            List<JobExecutionContext> currentlyExecuting = sched.getCurrentlyExecutingJobs();
            for (JobExecutionContext jobExecutionContext : currentlyExecuting) {
                sched.interrupt(jobExecutionContext.getJobDetail().getKey());
            }
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Shutdown the scheduler
     */
    public String shutdownScheduler(boolean waitForJobsToComplete) {
        try {
            //true = wait until all jobs have completed running before returning from the method call
            sched.shutdown(waitForJobsToComplete);
            //shutdown the scheduledThreadPool that is logging the scheduler status to database
            scheduledThreadPool.shutdown();
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Place the scheduler in standby
     * the scheduler will not fire triggers / execute jobs
     */
    public String standbyScheduler() {
        try {
            sched.standby();
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    // Starts the Scheduler's threads that fire Triggers
    public String startScheduler() {
        try {
            sched.start();
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    // Pause all triggers - similar to calling pauseTriggerGroup(group) on every group, however, after using this method resumeAll() must be
    // called to clear the scheduler's state of 'remembering' that all new triggers will be paused as they are added
    public String pauseAll() {
        try {
            sched.pauseAll();
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    // Resume (un-pause) all triggers - similar to calling resumeTriggerGroup (group) on every group
    public String resumeAll() {
        try {
            sched.resumeAll();
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* List scheduler's jobs
     * 
     */
    public String listJobs() {
        JSONObject results_obj = new JSONObject();

        //JSON legend
        JSONArray legend_list = new JSONArray();
        legend_list.add("Job Name");
        legend_list.add("Job Group");
        legend_list.add("Job Description");
        legend_list.add("Concurrent Execution");
        legend_list.add("Durability");
        legend_list.add("Persist Job Data After Execution");
        legend_list.add("Requests Recovery");
        legend_list.add("Next Fire Time");
        results_obj.put(0, legend_list);

        try {
            int counter = 1;
            for (String groupName : sched.getJobGroupNames()) {
                for (JobKey jobKey : sched.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {

                    JobDetail jobDetail = sched.getJobDetail(jobKey);
                    String jobName = jobKey.getName();
                    String jobGroup = jobKey.getGroup();
                    String jobDescription = jobDetail.getDescription();
                    String jobConcurrentExecution = jobDetail.isConcurrentExectionDisallowed() ? "false" : "true";
                    String jobDurable = jobDetail.isDurable() ? "true" : "false";
                    String jobPersistJobDataAfterExecution = jobDetail.isPersistJobDataAfterExecution() ? "true" : "false";
                    String jobrequestsRecovery = jobDetail.requestsRecovery() ? "true" : "false";

                    //get job's trigger
                    List<Trigger> triggers = (List<Trigger>) sched.getTriggersOfJob(jobKey);
                    Date nextFireTime = null;
                    if (triggers != null && triggers.size() > 0) {
                        nextFireTime = triggers.get(0).getNextFireTime();
                    }

                    JSONArray list = new JSONArray();
                    list.add(jobName);
                    list.add(jobGroup);
                    list.add(jobDescription);
                    list.add(jobConcurrentExecution);
                    list.add(jobDurable);
                    list.add(jobPersistJobDataAfterExecution);
                    list.add(jobrequestsRecovery);

                    if (triggers != null && triggers.size() > 0) {
                        list.add(nextFireTime);
                    } else {
                        list.add("");
                    }

                    results_obj.put(counter, list);
                    counter++;
                }
            }
            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* List scheduler's triggers of a job
     * 
     */
    public String listJobTriggers(JobKey jobKey) {
        JSONObject results_obj = new JSONObject();

        //JSON legend
        JSONArray legend_list = new JSONArray();
        legend_list.add("Trigger Name");
        legend_list.add("Trigger Group");
        legend_list.add("Calendar Name");
        legend_list.add("Description");
        legend_list.add("End Time");
        legend_list.add("Final Fire Time");
        legend_list.add("Misfire Instruction");
        legend_list.add("Next Fire Time");
        legend_list.add("Previous Fire Time");
        legend_list.add("Priority");
        legend_list.add("Start Time");
        legend_list.add("May Fire Again");
        results_obj.put(0, legend_list);

        try {
            int counter = 1;
            //get job's trigger
            List<Trigger> triggers = (List<Trigger>) sched.getTriggersOfJob(jobKey);
            for (Trigger trigger : triggers) {
                String name = trigger.getKey().getName();
                String group = trigger.getKey().getGroup();
                String calendarName = trigger.getCalendarName() != null ? trigger.getCalendarName() : "";
                String description = trigger.getDescription() != null ? trigger.getDescription() : "";
                String endTime = trigger.getEndTime() != null ? trigger.getEndTime().toString() : "";
                String finalFireTime = trigger.getFinalFireTime() != null ? trigger.getFinalFireTime().toString() : "";
                String misfireInstruction = Integer.toString(trigger.getMisfireInstruction());
                String nextFireTime = trigger.getNextFireTime() != null ? trigger.getNextFireTime().toString() : "";
                String previousFireTime = trigger.getPreviousFireTime() != null ? trigger.getPreviousFireTime().toString() : "";
                String priority = Integer.toString(trigger.getPriority());
                String startTime = trigger.getStartTime() != null ? trigger.getStartTime().toString() : "";
                String mayFireAgain = trigger.mayFireAgain() ? "true" : "false";

                JSONArray list = new JSONArray();
                list.add(name);
                list.add(group);
                list.add(calendarName);
                list.add(description);
                list.add(endTime);
                list.add(finalFireTime);
                list.add(misfireInstruction);
                list.add(nextFireTime);
                list.add(previousFireTime);
                list.add(priority);
                list.add(startTime);
                list.add(mayFireAgain);

                results_obj.put(counter, list);
                counter++;
            }
            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    // get scheduler's executing jobs as a json
    public String getCurrentlyExecutingJobs() {
        JSONObject results_obj = new JSONObject();

        //JSON legend
        JSONArray legend_list = new JSONArray();
        legend_list.add("Job Name");
        legend_list.add("Job Group");
        legend_list.add("Next Fire Time");
        results_obj.put(0, legend_list);

        try {
            int counter = 0;
            for (JobExecutionContext context : sched.getCurrentlyExecutingJobs()) {
                JobDetail jobDetail = context.getJobDetail();

                String jobName = jobDetail.getKey().getName();
                String jobGroup = jobDetail.getKey().getGroup();

                //get job's trigger
                List<Trigger> triggers = (List<Trigger>) sched.getTriggersOfJob(jobDetail.getKey());
                String nextFireTime = triggers != null ? triggers.get(0).getNextFireTime().toString() : "";

                JSONArray list = new JSONArray();
                list.add(jobName);
                list.add(jobGroup);
                list.add(nextFireTime);

                results_obj.put(counter, list);
                counter++;
            }
            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Get the JobDetail for the Job instance with the given key
     * @param jobName the job's name
     * @param jobGroup the job's group
     * @return job details in JSON format
     */
    public String getJobDetail(String jobName, String jobGroup) {
        JSONObject results_obj = new JSONObject();
        try {
            JobDetail jobDetail = sched.getJobDetail(JobKey.jobKey(jobName, jobGroup));
            JobDataMap jobDataMap = jobDetail.getJobDataMap();
            Iterator iter = jobDataMap.keySet().iterator();
            while (iter.hasNext()) {
                String key = (String) iter.next();
                results_obj.put(iter.next() + "", jobDataMap.get(key) + "");
            }
            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Get the names of all Trigger groups that are paused
     * @return names of paused trigger groups in JSON format
     */
    public String getPausedTriggerGroups() {
        JSONObject results_obj = new JSONObject();
        try {
            Set<String> triggerGroups = sched.getPausedTriggerGroups();
            Iterator iter = triggerGroups.iterator();
            int counter = 0;
            while (iter.hasNext()) {
                String triggerGroup = (String) iter.next();
                results_obj.put(counter, triggerGroup);
                counter++;
            }
            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Get the names of all known JobDetail groups
     * @param jobName the job's name
     * @param jobGroup the job's group
     * @return names of JobDetail groups in JSON format
     */
    public String getJobGroupNames() {
        JSONObject results_obj = new JSONObject();
        try {
            List<String> groupNames = sched.getJobGroupNames();
            int counter = 0;
            for (String groupName : groupNames) {
                results_obj.put(counter, groupName);
                counter++;
            }
            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Get the names of all known trigger groups
     * @param jobName the job's name
     * @param jobGroup the job's group
     * @return names of trigger groups in JSON format
     */
    public String getTriggerGroupNames() {
        JSONObject results_obj = new JSONObject();
        try {
            List<String> groupNames = sched.getTriggerGroupNames();
            int counter = 0;
            for (String groupName : groupNames) {
                results_obj.put(counter, groupName);
                counter++;
            }
            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Get the names of all the Triggers in the given group
     * @return names of triggers in JSON format
     */
    public String getTriggerKeys(String group) {
        JSONObject results_obj = new JSONObject();
        try {
            GroupMatcher<TriggerKey> gm = GroupMatcher.triggerGroupEquals(group);
            Set<TriggerKey> triggerGroups = sched.getTriggerKeys(gm);
            Iterator iter = triggerGroups.iterator();
            int counter = 0;
            while (iter.hasNext()) {
                TriggerKey triggerKey = (TriggerKey) iter.next();
                results_obj.put(counter, triggerKey.getName());
                counter++;
            }
            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Get all Triggers that are associated with the identified JobDetail
     * @return triggers associated with job in JSON format
     */
    public String getTriggersOfJob(String jobName, String jobGroup) {
        JSONObject results_obj = new JSONObject();
        try {
            List<Trigger> triggers = (List<Trigger>) sched.getTriggersOfJob(JobKey.jobKey(jobName, jobGroup));
            int counter = 0;
            for (Trigger trigger : triggers) {
                results_obj.put(counter, new String[]{trigger.getKey().getName(), trigger.getKey().getName()});
                counter++;
            }
            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Get start time, previous fire time, next fire time, end fire time for a job
     * @return start time, previous fire time, next fire time, end fire time
     */
    public String getJobFireTimes(String jobName, String jobGroup) {
        JSONObject results_obj = new JSONObject();
        try {
            List<Trigger> triggers = (List<Trigger>) sched.getTriggersOfJob(JobKey.jobKey(jobName, jobGroup));

            Date startTime = new Date();
            Date previousFireTime = new Date();
            previousFireTime.setTime(0);
            Date nextFireTime = new Date();
            Date endTime = new Date();
            endTime.setTime(0);

            //trigger state counters
            int blocked = 0;
            int complete = 0;
            int error = 0;
            int none = 0;
            int normal = 0;
            int paused = 0;

            for (Trigger trigger : triggers) {
                if (trigger.getStartTime().before(startTime)) {
                    startTime = trigger.getStartTime();
                    results_obj.put("startTime", startTime.toString());
                }
                if (trigger.getPreviousFireTime().after(previousFireTime)) {
                    previousFireTime = trigger.getPreviousFireTime();
                    results_obj.put("previousFireTime", startTime.toString());
                }
                if (trigger.getNextFireTime().before(nextFireTime)) {
                    nextFireTime = trigger.getNextFireTime();
                    results_obj.put("nextFireTime", startTime.toString());
                }
                if (trigger.getEndTime() != null && trigger.getEndTime().after(endTime)) {
                    endTime = trigger.getEndTime();
                    results_obj.put("endTime", startTime.toString());
                }

                //count trigger state
                if (sched.getTriggerState(trigger.getKey()).compareTo(TriggerState.ERROR) == 0) {
                    error++;
                } else if (sched.getTriggerState(trigger.getKey()).compareTo(TriggerState.BLOCKED) == 0) {
                    blocked++;
                } else if (sched.getTriggerState(trigger.getKey()).compareTo(TriggerState.PAUSED) == 0) {
                    paused++;
                } else if (sched.getTriggerState(trigger.getKey()).compareTo(TriggerState.NORMAL) == 0) {
                    normal++;
                } else if (sched.getTriggerState(trigger.getKey()).compareTo(TriggerState.COMPLETE) == 0) {
                    complete++;
                } else if (sched.getTriggerState(trigger.getKey()).compareTo(TriggerState.NONE) == 0) {
                    none++;
                }
            }

            if (blocked == triggers.size()) {
                results_obj.put("state", "BLOCKED");
            } else if (complete == triggers.size()) {
                results_obj.put("state", "COMPLETE");
            } else if (error == triggers.size()) {
                results_obj.put("state", "ERROR");
            } else if (none == triggers.size()) {
                results_obj.put("state", "NONE");
            } else if (normal == triggers.size()) {
                results_obj.put("state", "NORMAL");
            } else if (paused == triggers.size()) {
                results_obj.put("state", "PAUSED");
            }

            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Get the current state of the identified Trigger
     * @return trigger state
     */
    public String getTriggerState(String triggerName, String triggerGroup) {
        try {
            return sched.getTriggerState(TriggerKey.triggerKey(triggerName, triggerGroup)).toString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Pause all of the JobDetails in the matching group by pausing all of their Triggers
     * @return true
     */
    public String pauseJobs(String groupName) {
        try {
            GroupMatcher<JobKey> gm = GroupMatcher.jobGroupEquals(groupName);
            sched.pauseJobs(gm); //method returns void
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Pause all of the Triggers in the matching group
     * @return true
     */
    public String pauseTriggers(String groupName) {
        try {
            GroupMatcher<TriggerKey> gm = GroupMatcher.triggerGroupEquals(groupName);
            sched.pauseTriggers(gm); //method returns void
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Resume (un-pause) all of the Triggers in matching group
     * @return true
     */
    public String resumeTriggers(String groupName) {
        try {
            GroupMatcher<TriggerKey> gm = GroupMatcher.triggerGroupEquals(groupName);
            sched.resumeTriggers(gm); //method returns void
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Get the scheduler
     * @return scheduler instance
     */
    public Scheduler getScheduler() {
        return sched;
    }

    /* Build a triggerBuilder
     * @return TriggerBuilder
     */
    public TriggerBuilder buildTrigger(JSONObject jsonObject) {
        try {
            TriggerBuilder tmp = newTrigger();
            SimpleScheduleBuilder sbt = SimpleScheduleBuilder.simpleSchedule();
            Object t = tmp;
            boolean withSchedule = false; //set to true if a schedule is used
            Iterator it = jsonObject.keySet().iterator();
            JSONArray array;
            while (it.hasNext()) {
                String key = (String) it.next();
                Object value = jsonObject.get(key);
                switch (key) {
                    //Set the time at which the Trigger will no longer fire - even if it's schedule has remaining repeats
                    case "endAt":
                        t = ((TriggerBuilder<Trigger>) t).endAt(new Date(Long.parseLong((String) value))); //number of milliseconds from January 1st 1970
                        break;
                    //Set the identity of the Job which should be fired by the produced Trigger, by extracting the JobKey from the given job
                    case "forJobDetail":
                        array = (JSONArray) value;
                        JobKey jobKey = JobKey.jobKey((String) array.get(0), (String) array.get(1)); //value[0]=jobName, value[1]=jobGroup
                        if (jobKey != null) {
                            JobDetail jobDetail = sched.getJobDetail(jobKey);
                            if (jobDetail != null) {
                                t = ((TriggerBuilder<Trigger>) t).forJob(jobDetail);
                            } else {
                                throw new SchedulerException();
                            }
                        }
                        break;
                    //Set the identity of the Job which should be fired by the produced Trigger
                    case "forJobKey":
                        array = (JSONArray) value;
                        t = ((TriggerBuilder<Trigger>) t).forJob(JobKey.jobKey((String) array.get(0), (String) array.get(1))); //value[0]=jobName, value[1]=jobGroup
                        break;
                    //Set the identity of the Job which should be fired by the produced Trigger - a JobKey will be produced with the given name and default group
                    case "forJobName":
                        t = ((TriggerBuilder<Trigger>) t).forJob((String) value);
                        break;
                    //Set the identity of the Job which should be fired by the produced Trigger - a JobKey will be produced with the given name and group
                    case "forJobNameGroup":
                    //This case (withJobIdentityNameGroup) could be called when invoking this method (through scheduleJob of index.jsp), when adding a new trigger to an existing job.
                    //In that case (in the method scheduleJob of this class), the newly built job is trashed and not added to the scheduler. The trigger must then contain the job name and group in order to successfully add it
                    //to the scheduler
                    case "withJobIdentityNameGroup":
                        array = (JSONArray) value;
                        t = ((TriggerBuilder<Trigger>) t).forJob((String) array.get(0), (String) array.get(1)); //value[0]=jobName, value[1]=jobGroup
                        break;
                    //Set the name of the Calendar that should be applied to this Trigger's schedule
                    case "modifiedByCalendar":
                        t = ((TriggerBuilder<Trigger>) t).modifiedByCalendar((String) value);
                        break;
                    //Set the time the Trigger should start at - the trigger may or may not fire at this time - depending upon the schedule configured for the Trigger
                    case "startAt":
                        t = ((TriggerBuilder<Trigger>) t).startAt(new Date(Long.parseLong((String) value))); //number of milliseconds from January 1st 1970
                        break;
                    //Set the time the Trigger should start at to the current moment - the trigger may or may not fire at this time - depending upon the schedule configured for the Trigger
                    case "startNow":
                        t = ((TriggerBuilder<Trigger>) t).startNow(); //number of milliseconds from January 1st 1970
                        break;
                    //case "usingJobData":
                    //
                    //break;
                    //Set the given (human-meaningful) description of the Trigger
                    case "withDescription":
                        t = ((TriggerBuilder<Trigger>) t).withDescription((String) value);
                        break;
                    //Use a TriggerKey with the given name and default group to identify the Trigger
                    case "withIdentityName":
                        t = ((TriggerBuilder<Trigger>) t).withIdentity((String) value);
                        break;
                    //Use a TriggerKey with the given name and group to identify the Trigger
                    case "withIdentityNameGroup":
                        array = (JSONArray) value;
                        t = ((TriggerBuilder<Trigger>) t).withIdentity((String) array.get(0), (String) array.get(1)); //value[0]=triggerName, value[1]=triggerGroup
                        break;
                    //Use the given TriggerKey to identify the Trigger
                    case "withIdentityTriggerKey":
                        t = ((TriggerBuilder<Trigger>) t).withIdentity(TriggerKey.triggerKey((String) value));
                        break;
                    //Set the Trigger's priority
                    case "withPriority":
                        t = ((TriggerBuilder<Trigger>) t).withPriority(Integer.parseInt((String) value));
                        break;
                    //********withSchedule********
                    //Specify that the trigger will repeat indefinitely
                    case "repeatForever":
                        sbt = value.equals("true") ? sbt.repeatForever() : sbt;
                        withSchedule = true;
                        break;
                    //Specify a repeat interval in minutes - which will then be multiplied by 60 * 60 * 1000 to produce milliseconds
                    case "withIntervalInHours":
                        sbt = sbt.withIntervalInHours(Integer.parseInt((String) value));
                        withSchedule = true;
                        break;
                    //Specify a repeat interval in milliseconds
                    case "withIntervalInMilliseconds":
                        sbt = sbt.withIntervalInMilliseconds(Integer.parseInt((String) value));
                        withSchedule = true;
                        break;
                    //Specify a repeat interval in minutes - which will then be multiplied by 60 * 1000 to produce milliseconds
                    case "withIntervalInMinutes":
                        sbt = sbt.withIntervalInMinutes(Integer.parseInt((String) value));
                        withSchedule = true;
                        break;
                    //Specify a repeat interval in seconds - which will then be multiplied by 1000 to produce milliseconds
                    case "withIntervalInSeconds":
                        sbt = sbt.withIntervalInSeconds(Integer.parseInt((String) value));
                        withSchedule = true;
                        break;
                    //If the Trigger misfires, use the SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW instruction
                    case "withMisfireHandlingInstructionFireNow":
                        sbt = sbt.withMisfireHandlingInstructionFireNow();
                        withSchedule = true;
                        break;
                    //If the Trigger misfires, use the Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY instruction
                    case "withMisfireHandlingInstructionIgnoreMisfires":
                        sbt = sbt.withMisfireHandlingInstructionIgnoreMisfires();
                        withSchedule = true;
                        break;
                    //If the Trigger misfires, use the SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_EXISTING_COUNT instruction
                    case "withMisfireHandlingInstructionNextWithExistingCount":
                        sbt = sbt.withMisfireHandlingInstructionNextWithExistingCount();
                        withSchedule = true;
                        break;
                    //If the Trigger misfires, use the SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT instruction
                    case "withMisfireHandlingInstructionNextWithRemainingCount":
                        sbt = sbt.withMisfireHandlingInstructionNextWithRemainingCount();
                        withSchedule = true;
                        break;
                    //If the Trigger misfires, use the SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT instruction
                    case "withMisfireHandlingInstructionNowWithExistingCount":
                        sbt = sbt.withMisfireHandlingInstructionNowWithExistingCount();
                        withSchedule = true;
                        break;
                    //If the Trigger misfires, use the SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_REMAINING_REPEAT_COUNT instruction
                    case "withMisfireHandlingInstructionNowWithRemainingCount":
                        sbt = sbt.withMisfireHandlingInstructionNowWithRemainingCount();
                        withSchedule = true;
                        break;
                    //Specify a the number of time the trigger will repeat - total number of firings will be this number + 1  
                    case "withRepeatCount":
                        sbt = sbt.withRepeatCount(Integer.parseInt((String) value));
                        withSchedule = true;
                        break;
                }
                //it.remove(); // avoids a ConcurrentModificationException
            }
            //request.getParameterMap();
            if (withSchedule) {
                t = ((TriggerBuilder<Trigger>) t).withSchedule(sbt);
            }
            return ((TriggerBuilder<Trigger>) t); //the build is not done here (.build())
        } catch (SchedulerException e) {
            return null;
        }
    }

    /* Build a jobbuilder
     * @return JobBuilder
     * reserved jobDataMap values:
     * #isNonConcurrent, used in this method
     * #url, used in this method and in the execute() method of RESTJob, RESTJobStateful classes
     * #notificationEmail, used in the execute() method of RESTJob, RESTJobStateful, ProcessExecutor, ProcessExecutorStateful, DumbJob, DumbJobStateful classes
     * #nextJobs, used in the execute() method of RESTJob, RESTJobStateful, ProcessExecutor, ProcessExecutorStateful classes
     * #processParameters, used in the execute() method of ProcessExecutor, ProcessExecutorStateful classes
     * #jobConstraints, used in the execute()  method of JobStoreTXCustom class
     * #elasticJobConstraints, used in the execute() method of ElasticJob, ElasticJobStateful classes
     * #url, used in the execute() method of RESTAppMetricJob, RESTAppMetricJobStateful classes
     * #timeout (optional, default = 5000), used in the execute() method of RESTAppMetricJob, RESTAppMetricJobStateful classes
     */
    public JobBuilder buildJob(JSONObject jsonObject) {
        JobBuilder t;
        //if the url parameter is not null, then istantiate the RESTJob class
        JSONObject jobDataMap = (JSONObject) jsonObject.get("jobDataMap");

        //get the class type for this job (e.g., RESTJob, ProcessExecutorJob, RESTXMLJob)
        String jobClass = (String) jsonObject.get("jobClass");

        //set the job class
        switch (jobClass) {
            case "DumbJob":
                //if isNonConcurrent = true, then istantiate the DumbJobStateful class
                if (jobDataMap != null && jobDataMap != null && jobDataMap.get("#isNonConcurrent") != null && ((String) jobDataMap.get("#isNonConcurrent")).equals("true")) {
                    t = newJob(DumbJobStateful.class);
                } else {
                    t = newJob(DumbJob.class);
                }
                break;
            case "ProcessExecutorJob":
                //if isNonConcurrent = true, then istantiate the ProcessExecutorStateful class
                if (jobDataMap != null && jobDataMap.get("#isNonConcurrent") != null && ((String) jobDataMap.get("#isNonConcurrent")).equals("true")) {
                    t = newJob(ProcessExecutorStateful.class);
                } else {
                    t = newJob(ProcessExecutor.class);
                }
                break;
            case "RESTJob":
                //if isNonConcurrent = true, then istantiate the RESTJobStateful class
                if (jobDataMap != null && jobDataMap.get("#isNonConcurrent") != null && (jobDataMap.get("#isNonConcurrent")).equals("true")) {
                    t = newJob(RESTJobStateful.class);
                } else {
                    t = newJob(RESTJob.class);
                }
                break;
            case "RESTXMLJob":
                //if isNonConcurrent = true, then istantiate the RESTXMLJobStateful class
                if (jobDataMap != null && jobDataMap.get("#isNonConcurrent") != null && (jobDataMap.get("#isNonConcurrent")).equals("true")) {
                    t = newJob(RESTXMLJobStateful.class);
                } else {
                    t = newJob(RESTXMLJob.class);
                }
                break;
            case "RESTKBJob":
                //if isNonConcurrent = true, then istantiate the RESTJobStateful class
                if (jobDataMap != null && jobDataMap.get("#isNonConcurrent") != null && (jobDataMap.get("#isNonConcurrent")).equals("true")) {
                    t = newJob(RESTKBJobStateful.class);
                } else {
                    t = newJob(RESTKBJob.class);
                }
                break;
            case "RESTCheckSLAJob":
                //if isNonConcurrent = true, then istantiate the RESTCheckSLAJobStateful class
                if (jobDataMap != null && jobDataMap.get("#isNonConcurrent") != null && (jobDataMap.get("#isNonConcurrent")).equals("true")) {
                    t = newJob(RESTCheckSLAJobStateful.class);
                } else {
                    t = newJob(RESTCheckSLAJob.class);
                }
                break;
            case "ElasticJob":
                //if isNonConcurrent = true, then istantiate the ElasticJobStateful class
                if (jobDataMap != null && jobDataMap.get("#isNonConcurrent") != null && (jobDataMap.get("#isNonConcurrent")).equals("true")) {
                    t = newJob(ElasticJobStateful.class);
                } else {
                    t = newJob(ElasticJob.class);
                }
                break;
            case "RESTAppMetricJob":
                //if isNonConcurrent = true, then istantiate the RESTAppMetricJobStateful class
                if (jobDataMap != null && jobDataMap.get("#isNonConcurrent") != null && (jobDataMap.get("#isNonConcurrent")).equals("true")) {
                    t = newJob(RESTAppMetricJobStateful.class);
                } else {
                    t = newJob(RESTAppMetricJob.class);
                }
                break;
            default:
                t = newJob(DumbJob.class);
                break;
        }

        //DUMB JOB
        /*if (jobDataMap == null || (jobDataMap.get("#url") == null && jobDataMap.get("#processParameters") == null)) {
         //if isNonConcurrent = true, then istantiate the DumbJobStateful class
         if (jobDataMap != null && jobDataMap.get("#isNonConcurrent") != null && ((String) jobDataMap.get("#isNonConcurrent")).equals("true")) {
         t = newJob(DumbJobStateful.class);
         } else {
         t = newJob(DumbJob.class);
         }
         //using dumb data
         //Add the given key-value pair to the JobDetail's JobDataMap
         //t = t.usingJobData("jobSays", "Hello World!");
         //t = t.usingJobData("myFloatValue", 3.141f);
         } //PROCESS EXECUTOR JOB
         else if (jobDataMap.get("#processParameters") != null) {
         //if isNonConcurrent = true, then istantiate the ProcessExecutorStateful class
         if (jobDataMap.get("#isNonConcurrent") != null && ((String) jobDataMap.get("#isNonConcurrent")).equals("true")) {
         t = newJob(ProcessExecutorStateful.class);
         } else {
         t = newJob(ProcessExecutor.class);
         }
         } //REST JOB
         else if (jobDataMap.get("#url") != null && jobDataMap.get("#binding") == null) {
         //if isNonConcurrent = true, then istantiate the RESTJobStateful class
         if (jobDataMap.get("#isNonConcurrent") != null && (jobDataMap.get("#isNonConcurrent")).equals("true")) {
         t = newJob(RESTJobStateful.class);
         } else {
         t = newJob(RESTJob.class);
         }
         } //REST XML JOB
         else if (jobDataMap.get("#url") != null && jobDataMap.get("#binding") != null) {
         //if isNonConcurrent = true, then istantiate the RESTJobStateful class
         if (jobDataMap.get("#isNonConcurrent") != null && (jobDataMap.get("#isNonConcurrent")).equals("true")) {
         t = newJob(RESTXMLJobStateful.class);
         } else {
         t = newJob(RESTXMLJob.class);
         }
         } //ELSE
         else {
         t = newJob(DumbJob.class);
         }*/
        //set job data map values from a json job data map key => value, if defined
        if (jobDataMap != null) {
            Iterator dataMapIterator = jobDataMap.keySet().iterator();
            while (dataMapIterator.hasNext()) {
                String key = (String) dataMapIterator.next();
                String value = (String) jobDataMap.get(key);
                t = t.usingJobData(key, value);
            }
        }

        Iterator it = jsonObject.keySet().iterator();
        JSONArray array;
        while (it.hasNext()) {
            String key = (String) it.next();
            Object value = jsonObject.get(key);
            switch (key) {
                //Set whether or not the Job should remain stored after it is orphaned
                //Whether or not the Job should remain stored after it is orphaned (no Triggers point to it)
                //If a job is non-durable, it is automatically deleted from the scheduler once there are no longer
                //any active triggers associated with it. In other words, non-durable jobs have a life span bounded by the existence of its triggers
                case "storeDurably":
                    t = t.storeDurably(((String) value).equals("true"));
                    break;
                case "usingJobDataBoolean":
                    for (String s : (String[]) value) {
                        t = t.usingJobData("usingJobDataBoolean", s.equals("true") ? Boolean.TRUE : Boolean.FALSE);
                    }
                    break;
                case "usingJobDataDouble":
                    for (String s : (String[]) value) {
                        t = t.usingJobData("usingJobDataDouble", Double.parseDouble(s));
                    }
                    break;
                case "usingJobDataFloat":
                    for (String s : (String[]) value) {
                        t = t.usingJobData("usingJobDataFloat", Float.parseFloat(s));
                    }
                    break;
                case "usingJobDataInteger":
                    for (String s : (String[]) value) {
                        t = t.usingJobData("usingJobDataInteger", Integer.parseInt(s));
                    }
                    break;
                case "usingJobDataLong":
                    for (String s : (String[]) value) {
                        t = t.usingJobData("usingJobDataLong", Long.parseLong(s));
                    }
                    break;
                case "usingJobDataString":
                    for (String s : (String[]) value) {
                        t = t.usingJobData("usingJobDataString", s);
                    }
                    break;
                //Set the description given to the Job instance by its creator (if any)
                case "withJobDescription":
                    t = t.withDescription((String) value);
                    break;
                //Set the job name
                case "withJobIdentityName":
                    t = t.withIdentity(JobKey.jobKey((String) value)); //value[0]=jobName
                    break;
                //Set the job name and group
                case "withJobIdentityNameGroup":
                    array = (JSONArray) value;
                    t = t.withIdentity(JobKey.jobKey((String) array.get(0), (String) array.get(1))); //value[0]=jobName, value[1]=jobGroup
                    break;
                //In clustering mode, this parameter must be set to true to ensure job fail-over
                //Instructs the Scheduler whether or not the Job should be re-executed if a 'recovery' or 'fail-over' situation is encountered
                //If a job "requests recovery", and it is executing during the time of a 'hard shutdown' of the scheduler (i.e. the process it is
                //running within crashes, or the machine is shut off), then it is re-executed when the scheduler is started again. In this case, the
                //JobExecutionContext.Recovering property will return true
                case "requestRecovery":
                    t = t.requestRecovery(((String) value).equals("true"));
                    break;
                //REST call
                /*case "url":
                 t = t.usingJobData("url", (String) value);
                 break;
                 //store notificationEmail
                 case "notificationEmail":
                 t = t.usingJobData("notificationEmail", (String) value);
                 break;*/
            }
            //it.remove(); // avoids a ConcurrentModificationException
        }
        return t; //the build is not done here (.build())
    }

    public String scheduleJob(JSONObject jsonObject) {
        try {
            TriggerBuilder triggerBuilder = buildTrigger(jsonObject);
            Trigger trigger = triggerBuilder.build();
            JobBuilder jobBuilder = buildJob(jsonObject);
            JobDetail jobDetail = jobBuilder.build(); //build a new jobDetail
            Date date;
            //if the job with that name.group doesn't exists, add the new builded job to the scheduler with the trigger
            if (!sched.checkExists(JobKey.jobKey(jobDetail.getKey().getName(), jobDetail.getKey().getGroup()))) {
                date = sched.scheduleJob(jobDetail, trigger);
            } else {
                //jobDetail = sched.getJobDetail(JobKey.jobKey(jobDetail.getKey().getName(), jobDetail.getKey().getGroup())); //set the jobDetail to the existing job
                date = sched.scheduleJob(trigger);
            }

            /*//if email data is defined, then create a new job listener, set the notificationEmail and add it to the scheduler
             if (jobDetail.getJobDataMap().containsKey("notificationEmail")) {
             //Adding a JobListener that is interested in a particular job (send email upon job finished)
             //Set the job listener name (jobName.jobGroup) to be able to remove it later
             SendEmailJobListener sml = new SendEmailJobListener(jobDetail.getKey().getName() + "." + jobDetail.getKey().getGroup(), jobDetail.getJobDataMap().getString("notificationEmail"));
             sched.getListenerManager().addJobListener(sml, KeyMatcher.keyEquals(jobDetail.getKey()));
             }
             //else, there is no job listener to remove, since this method creates a new job*/
            return date.toString();
        } catch (SchedulerException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    // update a trigger of a job
    public String rescheduleJob(JSONObject jsonObject, String oldTriggerName, String oldTriggerGroup) {
        try {
            //Map<String, String[]> m = new HashMap<>();
            //m.putAll(map);
            TriggerBuilder triggerBuilder = buildTrigger(jsonObject);
            Trigger trigger = triggerBuilder.build();
            Date date = sched.rescheduleJob(TriggerKey.triggerKey(oldTriggerName, oldTriggerGroup), trigger);
            return date.toString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    // Remove the indicated Trigger from the scheduler
    public String unscheduleJob(String triggerName, String triggerGroup) {
        try {
            TriggerKey triggerKey = TriggerKey.triggerKey(triggerName, triggerGroup);
            if (sched.checkExists(triggerKey)) {
                sched.unscheduleJob(triggerKey);
                return "true";
            } else {
                return "Trigger does not exist";
            }
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    // add the new job to the scheduler, instructing it to "replace" the existing job with the given name and group (if any)
    public String updateJob(JSONObject jsonObject) {
        try {
            JobBuilder jobBuilder = buildJob(jsonObject);
            JobDetail jobDetail = jobBuilder.build();
            JobDataMap jobDataMap;

            // store, and set overwrite flag to 'true', replace a job with the same name.group in the scheduler     
            sched.addJob(jobDetail, true);
            return "true";

            /*//if email data is defined, then create or update a job listener
             if (jobDetail.getJobDataMap().containsKey("notificationEmail")) {
             //Adding a JobListener that is interested in a particular job (send email upon job finished)
             //Set the job listener name (jobName.jobGroup) to be able to remove it later
             SendEmailJobListener sml = null;
             //if the job listener associated to this job already exists, then retrieve it and update the email with the new value
             sml = (SendEmailJobListener) sched.getListenerManager().getJobListener(jobDetail.getKey().getName() + "." + jobDetail.getKey().getGroup());
             if (sml != null) {
             sml.setEmail(jobDetail.getJobDataMap().getString("notificationEmail"));
             } //else create a new job listener, set the notificationEmail and add it to the scheduler
             else {
             sml = new SendEmailJobListener(jobDetail.getKey().getName() + "." + jobDetail.getKey().getGroup(), jobDetail.getJobDataMap().getString("notificationEmail"));
             sched.getListenerManager().addJobListener(sml, KeyMatcher.keyEquals(jobDetail.getKey()));
             }
             } //if email is not defined, remove the SendEmailJobListener eventually present
             else {
             SendEmailJobListener sml = (SendEmailJobListener) sched.getListenerManager().getJobListener(jobDetail.getKey().getName() + "." + jobDetail.getKey().getGroup());
             if (sml != null) {
             sched.getListenerManager().removeJobListener(jobDetail.getKey().getName() + "." + jobDetail.getKey().getGroup());
             }
             }*/
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    // add the given Job to the Scheduler - with no associated Trigger. The Job will be 'dormant' until it is scheduled with a Trigger, or Scheduler.triggerJob() is called for it.
    // With the isDurable parameter set to true, a non-durable job can be stored. Once it is scheduled, it will resume normal non-durable behavior 
    // (i.e. be deleted once there are no remaining associated triggers).
    public String addJob(JSONObject jsonObject) {
        try {
            JobBuilder jobBuilder = buildJob(jsonObject);
            JobDetail jobDetail = jobBuilder.build();
            //if storeDurably is not defined, set it to true
            boolean storeDurably = true;
            if (jsonObject.containsKey("storeDurably")) {
                storeDurably = jsonObject.get("storeDurably").equals("true");
            }
            // store, and set overwrite flag to 'false', if an object with the same name.group already exists in the scheduler, this won't be added     
            sched.addJob(jobDetail, false, storeDurably);
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    // get the job data map as a json
    public String getJobDataMap(String jobName, String jobGroup) {
        try {
            JobDetail jobDetail = sched.getJobDetail(JobKey.jobKey(jobName, jobGroup));
            JobDataMap jobDataMap = jobDetail.getJobDataMap();
            JSONObject results_obj = new JSONObject();
            for (Map.Entry<String, Object> entry : jobDataMap.entrySet()) {
                results_obj.put(entry.getKey(), entry.getValue());
            }
            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return "";
        }
    }

    // get the notification email eventually set in the job data map
    public String getNotificationEmail(String jobName, String jobGroup) {
        try {
            JobDetail jobDetail = sched.getJobDetail(JobKey.jobKey(jobName, jobGroup));
            if (jobDetail != null) {
                return jobDetail.getJobDataMap().getString("#notificationEmail");
            } else {
                return "";
            }
        } catch (SchedulerException e) {
            return "";
        }
    }

    // get the list of triggers associated to a job as a json
    public String getJobTriggers(String jobName, String jobGroup) {
        try {
            List<? extends Trigger> list = sched.getTriggersOfJob(JobKey.jobKey(jobName, jobGroup));
            JSONObject results_obj = new JSONObject();
            int index = 0;
            for (Trigger t : list) {
                JSONArray jsonList = new JSONArray();
                jsonList.add(t.getKey().getName());
                jsonList.add(t.getKey().getGroup());
                results_obj.put(index, jsonList);
                index++;
            }
            return results_obj.toJSONString();
        } catch (SchedulerException e) {
            return "";
        }
    }

    // Trigger the identified JobDetail (execute it now)
    public String triggerJob(String jobName, String jobGroup) {
        try {
            JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
            if (sched.checkExists(jobKey)) {
                sched.triggerJob(jobKey);
                return "true";
            } else {
                return "Job does not exist";
            }
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    //pause a job
    public String pauseJob(String jobName, String jobGroup) {
        try {
            JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
            if (sched.checkExists(jobKey)) {
                sched.pauseJob(jobKey);
                return "true";
            } else {
                return "Job does not exist";
            }
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    //resume a job
    public String resumeJob(String jobName, String jobGroup) {
        try {
            JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
            if (sched.checkExists(jobKey)) {
                sched.resumeJob(jobKey);
                return "true";
            } else {
                return "Job does not exist";
            }
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    /* Resume (un-pause) all of the JobDetails in matching group
     * @return true
     */
    public String resumeJobs(String groupName) {
        try {
            GroupMatcher<JobKey> gm = GroupMatcher.jobGroupEquals(groupName);
            sched.resumeJobs(gm); //method returns void
            return "true";
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    //pause a trigger
    public String pauseTrigger(String triggerName, String triggerGroup) {
        try {
            TriggerKey triggerKey = TriggerKey.triggerKey(triggerName, triggerGroup);
            if (sched.checkExists(triggerKey)) {
                sched.pauseTrigger(triggerKey);
                return "true";
            } else {
                return "Trigger does not exist";
            }
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    // Resume (un-pause) the Trigger with the given key
    public String resumeTrigger(String triggerName, String triggerGroup) {
        try {
            TriggerKey triggerKey = TriggerKey.triggerKey(triggerName, triggerGroup);
            if (sched.checkExists(triggerKey)) {
                sched.resumeTrigger(triggerKey);
                return "true";
            } else {
                return "Trigger does not exist";
            }
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    //get scheduler metadata as json
    public String getSchedulerMetadata() {
        String result;
        try {
            SchedulerMetaData schedulerMetadata = sched.getMetaData();
            JSONObject results_obj = new JSONObject();

            JSONArray jsonList1 = new JSONArray();
            jsonList1.add(schedulerMetadata.getNumberOfJobsExecuted());
            jsonList1.add("Reports the number of jobs executed since the scheduler started");
            results_obj.put("Number of jobs executed", jsonList1);

            JSONArray jsonList2 = new JSONArray();
            jsonList2.add(schedulerMetadata.getRunningSince().toString());
            jsonList2.add("Reports the date at which the scheduler started running");
            results_obj.put("Running since", jsonList2);

            JSONArray jsonList3 = new JSONArray();
            jsonList3.add(schedulerMetadata.getSchedulerInstanceId());
            jsonList3.add("Reports the instance id of the scheduler");
            results_obj.put("Scheduler instance id", jsonList3);

            JSONArray jsonList4 = new JSONArray();
            jsonList4.add(schedulerMetadata.getSchedulerName());
            jsonList4.add("Reports the name of the scheduler");
            results_obj.put("Scheduler name", jsonList4);

            JSONArray jsonList5 = new JSONArray();
            jsonList5.add(schedulerMetadata.isInStandbyMode() ? "yes" : "no");
            jsonList5.add("Reports whether the scheduler is in standby mode");
            results_obj.put("Standby mode", jsonList5);

            JSONArray jsonList6 = new JSONArray();
            jsonList6.add(schedulerMetadata.isJobStoreClustered() ? "yes" : "no");
            jsonList6.add("Reports whether or not the scheduler's JobStore is clustered");
            results_obj.put("JobStore Clustered", jsonList6);

            JSONArray jsonList7 = new JSONArray();
            jsonList7.add(schedulerMetadata.isJobStoreSupportsPersistence() ? "yes" : "no");
            jsonList7.add("Reports whether or not the scheduler's JobStore instance supports persistence");
            results_obj.put("JobStore supports persistence", jsonList7);

            JSONArray jsonList8 = new JSONArray();
            jsonList8.add(schedulerMetadata.isSchedulerRemote() ? "yes" : "no");
            jsonList8.add("Reports whether the scheduler is being used remotely (via RMI)");
            results_obj.put("Remote Scheduler", jsonList8);

            JSONArray jsonList9 = new JSONArray();
            jsonList9.add(schedulerMetadata.isShutdown() ? "yes" : "no");
            jsonList9.add("Reports whether the scheduler has been shutdown");
            results_obj.put("Scheduler shutdown", jsonList9);

            JSONArray jsonList10 = new JSONArray();
            jsonList10.add(schedulerMetadata.isStarted() ? "yes" : "no");
            jsonList10.add("Reports whether the scheduler has been started");
            results_obj.put("Scheduler started", jsonList10);

            result = results_obj.toJSONString();

            jsonList1.clear();
            jsonList2.clear();
            jsonList3.clear();
            jsonList4.clear();
            jsonList5.clear();
            jsonList6.clear();
            jsonList7.clear();
            jsonList8.clear();
            jsonList9.clear();
            jsonList10.clear();
        } catch (SchedulerException e) {
            result = "";
        }
        return result;
    }

    //get the system status as a json
    public String getSystemStatus() {
        String result;
        OperatingSystemMXBean omx = ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean osMxBean = null;
        if (omx instanceof com.sun.management.OperatingSystemMXBean) {
            osMxBean = (com.sun.management.OperatingSystemMXBean) omx;
        }

        JSONObject results_obj = new JSONObject();

        JSONArray jsonList1 = new JSONArray();
        String arch = "";
        if (osMxBean != null) {
            arch = osMxBean.getArch();
        }
        jsonList1.add(arch != null ? arch : "");
        jsonList1.add("Returns the operating system architecture");
        results_obj.put("Operating System architecture", jsonList1);

        JSONArray jsonList2 = new JSONArray();
        int availableProcessors = -1;
        if (osMxBean != null) {
            availableProcessors = osMxBean.getAvailableProcessors();
        }
        jsonList2.add(Integer.toString(availableProcessors));
        jsonList2.add("Reports the number of processors available to the Java virtual machine");
        results_obj.put("Number of processors", jsonList2);

        JSONArray jsonList3 = new JSONArray();
        String name = "";
        if (osMxBean != null) {
            name = osMxBean.getName();
        }
        jsonList3.add(name != null ? name : "");
        jsonList3.add("Reports the operating system name");
        results_obj.put("Operating System name", jsonList3);

        JSONArray jsonList4 = new JSONArray();
        double systemLoadAverage = -1;
        if (osMxBean != null) {
            systemLoadAverage = osMxBean.getSystemLoadAverage();
        }
        jsonList4.add(Double.toString(systemLoadAverage));
        jsonList4.add("Reports the system load average for the last minute. The system load average is the sum of the number of runnable entities queued to the available processors and the number of runnable entities running on the available processors averaged over a period of time. The way in which the load average is calculated is operating system specific but is typically a damped time-dependent average.\n"
                + "\n"
                + "If the load average is not available, a negative value is returned."
                + "\n"
                + "This value is designed to provide a hint about the system load and may be queried frequently. The load average may be unavailable on some platform where it is expensive to implement this method");
        results_obj.put("System Load average", jsonList4);

        JSONArray jsonList5 = new JSONArray();
        String version = "";
        if (osMxBean != null) {
            version = osMxBean.getVersion();
        }
        jsonList5.add(version != null ? version : "");
        jsonList5.add("Reports the operating system version");
        results_obj.put("Operating System version", jsonList5);

        JSONArray jsonList6 = new JSONArray();
        long committedVirtualMemorySize = -1;
        if (osMxBean != null) {
            committedVirtualMemorySize = osMxBean.getCommittedVirtualMemorySize();
        }
        jsonList6.add(Long.toString(committedVirtualMemorySize));
        jsonList6.add("Reports the amount of virtual memory that is guaranteed to be available to the running process in bytes, or -1 if this operation is not supported");
        results_obj.put("Committed virtual memory", jsonList6);

        JSONArray jsonList7 = new JSONArray();
        long freePhysicalMemorySize = -1;
        if (osMxBean != null) {
            freePhysicalMemorySize = osMxBean.getFreePhysicalMemorySize();
        }
        jsonList7.add(Long.toString(freePhysicalMemorySize));
        jsonList7.add("Reports the amount of free physical memory in bytes");
        results_obj.put("Free physical memory", jsonList7);

        JSONArray jsonList8 = new JSONArray();
        long freeSwapSpaceSize = -1;
        if (osMxBean != null) {
            freeSwapSpaceSize = osMxBean.getFreeSwapSpaceSize();
        }
        jsonList8.add(Long.toString(freeSwapSpaceSize));
        jsonList8.add("Reports the amount of free swap space in bytes");
        results_obj.put("Free swap space", jsonList8);

        JSONArray jsonList9 = new JSONArray();
        double processCpuLoad = -1;
        if (osMxBean != null) {
            processCpuLoad = osMxBean.getProcessCpuLoad();
        }
        jsonList9.add(Double.toString(processCpuLoad));
        jsonList9.add("Returns the recent cpu usage for the Java Virtual Machine process. This value is a double in the [0.0, 1.0] interval. A value of 0.0 means that none of the CPUs were running threads from the JVM process during the recent period of time observed, while a value of 1.0 means that all CPUs were actively running threads from the JVM 100% of the time during the recent period being observed. Threads from the JVM include the application threads as well as the JVM internal threads. All values between 0.0 and 1.0 are possible depending of the activities going on in the JVM process and the whole system. If the Java Virtual Machine recent CPU usage is not available, the value reports a negative value");
        results_obj.put("CPU load (JVM)", jsonList9);

        JSONArray jsonList10 = new JSONArray();
        long processCpuTime = -1;
        if (osMxBean != null) {
            processCpuTime = osMxBean.getProcessCpuTime();
        }
        jsonList10.add(Long.toString(processCpuTime));
        jsonList10.add("Returns the cpu time used by the process on which the Java virtual machine is running in nanoseconds. The returned value is of nanoseconds precision but not necessarily nanoseconds accuracy. This value reports -1 if the platform does not support this operation");
        results_obj.put("Process CPU time", jsonList10);

        JSONArray jsonList11 = new JSONArray();
        double systemCpuLoad = -1;
        if (osMxBean != null) {
            systemCpuLoad = osMxBean.getSystemCpuLoad();
        }
        jsonList11.add(Double.toString(systemCpuLoad));
        jsonList11.add("Returns the recent cpu usage for the whole system. This value is a double in the [0.0, 1.0] interval. A value of 0.0 means that all CPUs were idle during the recent period of time observed, while a value of 1.0 means that all CPUs were actively running 100% of the time during the recent period being observed. All values between 0.0 and 1.0 are possible depending of the activities going on in the system. If the system recent cpu usage is not available, the value reports a negative value");
        results_obj.put("CPU load", jsonList11);

        JSONArray jsonList12 = new JSONArray();
        double totalPhysicalMemorySize = -1;
        if (osMxBean != null) {
            totalPhysicalMemorySize = osMxBean.getTotalPhysicalMemorySize();
        }
        jsonList12.add(Double.toString(totalPhysicalMemorySize));
        jsonList12.add("Returns the total amount of physical memory in bytes");
        results_obj.put("Total physical memory", jsonList12);

        JSONArray jsonList13 = new JSONArray();
        double totalSwapSpaceSize = -1;
        if (osMxBean != null) {
            totalSwapSpaceSize = osMxBean.getTotalSwapSpaceSize();
        }
        jsonList13.add(Double.toString(totalSwapSpaceSize));
        jsonList13.add("Returns the total amount of swap space in bytes");
        results_obj.put("Total swap space", jsonList13);

        result = results_obj.toJSONString();

        jsonList1.clear();
        jsonList2.clear();
        jsonList3.clear();
        jsonList4.clear();
        jsonList5.clear();
        jsonList6.clear();
        jsonList7.clear();
        jsonList8.clear();
        jsonList9.clear();
        jsonList10.clear();
        jsonList11.clear();
        jsonList12.clear();
        jsonList13.clear();

        return result;
    }

    public String setJobProgress(String fire_instance_id, double progress) {
        Connection conn = Main.getConnection();
        try {
            PreparedStatement preparedStatement = conn.prepareStatement("UPDATE quartz.QRTZ_STATUS SET PROGRESS = ? WHERE FIRE_INSTANCE_ID = ?");
            preparedStatement.setDouble(1, progress);
            preparedStatement.setString(2, fire_instance_id);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            Logger.getLogger(ExpressionTree.class.getName()).log(Level.SEVERE, null, ex);
            return "false";
        } finally {
            try {
                conn.close();
            } catch (SQLException ex) {
                Logger.getLogger(ExpressionTree.class.getName()).log(Level.SEVERE, null, ex);
                return "";
            }
        }
        return "true";
    }

    public String getJobProgress(String fire_instance_id) {
        Connection conn = Main.getConnection();
        String result = "";
        try {
            PreparedStatement preparedStatement = conn.prepareStatement("SELECT PROGRESS FROM quartz.QRTZ_STATUS WHERE FIRE_INSTANCE_ID = ?");
            preparedStatement.setString(1, fire_instance_id);
            ResultSet rs = preparedStatement.executeQuery();
            while (rs.next()) {
                result = rs.getString("PROGRESS");
            }
            return result;
        } catch (SQLException ex) {
            Logger.getLogger(ExpressionTree.class.getName()).log(Level.SEVERE, null, ex);
            return "false";
        } finally {
            try {
                conn.close();
            } catch (SQLException ex) {
                Logger.getLogger(ExpressionTree.class.getName()).log(Level.SEVERE, null, ex);
                return "";
            }
        }
    }

    //update the job data map with a json
    /*
     A Job instance can be defined as "stateful" or "non-stateful". 
     Non-stateful jobs only have their JobDataMap stored at the time 
     they are added to the scheduler. This means that any changes made 
     to the contents of the job data map during execution of the job will 
     be lost, and will not seen by the job the next time it executes.
     A stateful job is just the opposite - its JobDataMap is re-stored 
     after every execution of the job.
     You 'mark' a Job as stateful by having it implement the 
     StatefulJob interface, rather than the Job interface.
     */
    public String updateJobDataMap(JSONObject jsonObject) {
        try {
            JobKey jobKey = JobKey.jobKey((String) jsonObject.get("jobName"), (String) jsonObject.get("jobGroup"));
            if (sched.checkExists(jobKey)) {
                JobDetail jobDetail = sched.getJobDetail(jobKey);
                JobDataMap jobDataMap = jobDetail.getJobDataMap();
                Iterator it = jsonObject.keySet().iterator();
                while (it.hasNext()) {
                    String key = (String) it.next();
                    String value = (String) jsonObject.get(key);
                    jobDataMap.put(key, value);
                }
                sched.addJob(jobDetail, true); //replace the stored job with the new one
                return "true";
            } else {
                return "false";
            }
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    //TODO
    public String scheduleXML(JSONObject jsonObject) {
        try {
            String xml = (String) jsonObject.get("xml");
            String xmlpath = "/tmp/job.xml";
            CascadingClassLoadHelper helper = new CascadingClassLoadHelper();
            helper.initialize();
            XMLSchedulingDataProcessor proc = new XMLSchedulingDataProcessor(helper);
            //InputStream stream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            //proc.processStreamAndScheduleJobs(stream, xml, sched);
            //InputSource inputSource = new InputSource(new StringReader(xml));
            //proc.process(inputSource);
            /*FileWriter fstream = new FileWriter(xmlpath, false);
             BufferedWriter out = new BufferedWriter(fstream);
             out.write(xml);
             out.close();*/
            proc.processFileAndScheduleJobs(xmlpath, sched);
            return "true";
        } catch (ParserConfigurationException e) {
            return e.getMessage();
        } catch (SchedulerException e) {
            return e.getMessage();
        } catch (ClassNotFoundException e) {
            return e.getMessage();
        } catch (ParseException e) {
            return e.getMessage();
        } catch (FileNotFoundException e) {
            return e.getMessage();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    //replace a trigger
    public String replaceTrigger(String oldTriggerName, String oldTriggerGroup) {
        try {
            // Define a new Trigger 
            Trigger trigger = newTrigger()
                    .withIdentity("newTrigger", "group1")
                    .startNow()
                    .build();

            // tell the scheduler to remove the old trigger with the given key, and put the new one in its place
            Date date = sched.rescheduleJob(TriggerKey.triggerKey(oldTriggerName, oldTriggerGroup), trigger);
            return date.toString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    //update a trigger
    public String updateTrigger(String oldTriggerName, String oldTriggerGroup, Trigger newTrigger) {
        try {
            // retrieve the trigger
            Trigger oldTrigger = sched.getTrigger(TriggerKey.triggerKey(oldTriggerName, oldTriggerGroup));

            // obtain a builder that would produce the trigger
            TriggerBuilder tb = oldTrigger.getTriggerBuilder();

            Date date = sched.rescheduleJob(oldTrigger.getKey(), newTrigger);
            return date.toString();
        } catch (SchedulerException e) {
            return e.getMessage();
        }
    }

    void foo(String... args) {
        for (String arg : args) {
            System.out.println(arg);
        }
    }

    public static Main getInstance() {
        if (instance == null) {
            try {
                instance = new Main();
            } catch (Exception e) {
            }
        }
        return instance;
    }

    //truncate catalina.out (Tomcat log file)
    public String truncateCatalinaLog() {
        try {
            //create a ProcessBuilder instance for UNIX command >
            ProcessBuilder processBuilder = new ProcessBuilder(new String[]{"/bin/bash", "-c", "'' > " + prop.getProperty("tomcatLogsDirectory") + "/catalina.out"});
            //set the working directory
            //processBuilder.directory(new java.io.File(prop.getProperty("tomcatLogsDirectory")));
            //start new process
            java.lang.Process p = processBuilder.start();
            p.waitFor();
            return "true";
        } catch (IOException | InterruptedException e) {
            return e.getMessage();
        }
    }

    //get the database connection (used by other classes too)
    public static Connection getConnection() {
        try {
            if (connectionPool == null) {
                if (prop == null) {
                    prop = new Properties();
                    prop.load(Main.class.getResourceAsStream("quartz.properties"));
                }
                connectionPool = new ConnectionPool(prop.getProperty("org.quartz.dataSource.quartzDataSource.URL"), prop.getProperty("org.quartz.dataSource.quartzDataSource.user"), prop.getProperty("org.quartz.dataSource.quartzDataSource.password"));
                if (dataSource == null) {
                    dataSource = connectionPool.setUp();
                }
            }
            return dataSource.getConnection();
        } catch (IOException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    //get Connection Pool Info
    public String getConnectionPoolInfo() {
        JSONObject results_obj = new JSONObject();
        GenericObjectPool pool = connectionPool.getConnectionPool();

        results_obj.put("Lifo", pool.getLifo());
        results_obj.put("MaxActive", pool.getMaxActive());
        results_obj.put("MaxIdle", pool.getMaxIdle());
        results_obj.put("MaxWait", pool.getMaxWait());
        results_obj.put("MinEvictableIdleTimeMillis", pool.getMinEvictableIdleTimeMillis());
        results_obj.put("MinIdle", pool.getMinIdle());
        results_obj.put("NumActive", pool.getNumActive());
        results_obj.put("NumIdle", pool.getNumIdle());
        results_obj.put("NumTestsPerEvictionRun", pool.getNumTestsPerEvictionRun());
        results_obj.put("SoftMinEvictableIdleTimeMillis", pool.getSoftMinEvictableIdleTimeMillis());
        results_obj.put("TestOnBorrow", pool.getTestOnBorrow());
        results_obj.put("TestOnReturn", pool.getTestOnReturn());
        results_obj.put("TestWhileIdle", pool.getTestWhileIdle());
        results_obj.put("TimeBetweenEvictionRunsMillis", pool.getTimeBetweenEvictionRunsMillis());
        results_obj.put("WhenExhaustedAction", pool.getWhenExhaustedAction());

        return results_obj.toJSONString();
    }
}
