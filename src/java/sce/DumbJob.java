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

import java.util.Date;
import java.util.Map;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;

/*@DisallowConcurrentExecution
 * An annotation that marks a Job
 * class as one that must not have
 * multiple instances executed concurrently
 * (where instance is based-upon a JobDetail 
 * definition - or in other words based upon a JobKey). 
 */

/*@PersistJobDataAfterExecution
 * An annotation that marks a Job class as one that makes
 * updates to its JobDataMap during execution, and wishes the
 * scheduler to re-store the JobDataMap when execution completes.
 * Jobs that are marked with this annotation should also seriously
 * consider using the DisallowConcurrentExecution annotation, to avoid
 * data storage race conditions with concurrently executing job instances.
 * If you use the @PersistJobDataAfterExecution annotation, you should strongly
 * consider also using the @DisallowConcurrentExecution annotation, in order to
 * avoid possible confusion (race conditions) of what data was left stored when two
 * instances of the same job (JobDetail) executed concurrently.
 */
public class DumbJob implements Job {

    public DumbJob() {
    }

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        JobKey key = context.getJobDetail().getKey();

        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();

        //String jobSays = dataMap.getString("jobSays");
        //float myFloatValue = dataMap.getFloat("myFloatValue");
        //if notificationEmail is defined in the job data map, then send a notification email to it
        if (jobDataMap.containsKey("#notificationEmail")) {
            sendEmail(context, jobDataMap.getString("#notificationEmail"));
        }

        //Mail.sendMail("prova", "prova di email", "", "");
        //System.out.println("Instance " + key + " of DumbJob says: " + jobSays + ", and val is: " + myFloatValue);
        System.out.println("Instance " + key + " of DumbJob data map: ");
        for (Map.Entry<String, Object> entry : jobDataMap.entrySet()) {
            System.out.println("key: " + entry.getKey() + " value: " + entry.getValue());
        }
    }

    //send a notification email upon job completion
    public void sendEmail(JobExecutionContext context, String email) {
        Date d = new Date();
        String message = "Job was executed at " + d.toString() + "\n";
        //Returns the result (if any) that the Job set before its execution completed (the type of object set as the result is entirely up to the particular job).
        //The result itself is meaningless to Quartz, but may be informative to JobListeners or TriggerListeners that are watching the job's execution. 
        message += "Result: " + (context.getResult() != null ? context.getResult() : "") + "\n";
        //Get the unique Id that identifies this particular firing instance of the trigger that triggered this job execution. It is unique to this JobExecutionContext instance as well. 
        message += "Fire Instance Id: " + (context.getFireInstanceId() != null ? context.getFireInstanceId() : "") + "\n";
        //Get a handle to the Calendar referenced by the Trigger instance that fired the Job. 
        message += "Calendar: " + (context.getCalendar() != null ? context.getCalendar().getDescription() : "") + "\n";
        //The actual time the trigger fired. For instance the scheduled time may have been 10:00:00 but the actual fire time may have been 10:00:03 if the scheduler was too busy. 
        message += "Fire Time: " + (context.getFireTime() != null ? context.getFireTime() : "") + "\n";
        //the job name
        message += "Job Name: " + (context.getJobDetail().getKey() != null ? context.getJobDetail().getKey().getName() : "") + "\n";
        //the job group
        message += "Job Group: " + (context.getJobDetail().getKey() != null ? context.getJobDetail().getKey().getGroup() : "") + "\n";
        //The amount of time the job ran for (in milliseconds). The returned value will be -1 until the job has actually completed (or thrown an exception), and is therefore generally only useful to JobListeners and TriggerListeners.
        message += "RunTime: " + context.getJobRunTime() + "\n";
        //the next fire time
        message += "Next Fire Time: " + (context.getNextFireTime() != null && context.getNextFireTime().toString() != null ? context.getNextFireTime().toString() : "") + "\n";
        //the previous fire time
        message += "Previous Fire Time: " + (context.getPreviousFireTime() != null && context.getPreviousFireTime().toString() != null ? context.getPreviousFireTime().toString() : "") + "\n";
        //refire count
        message += "Refire Count: " + context.getRefireCount() + "\n";

        //job data map
        message += "Job data map: \n";
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        for (Map.Entry<String, Object> entry : jobDataMap.entrySet()) {
            message += entry.getKey() + " = " + entry.getValue() + "\n";
        }
        Mail.sendMail("SCE notification", message, email, null, null);
    }
}
