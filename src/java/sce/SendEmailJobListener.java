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

import java.util.Map;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import java.util.Date;

public class SendEmailJobListener implements JobListener {

    private final String name;
    private String email;

    public SendEmailJobListener(String name, String email) {
        this.name = name;
        this.email = email;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        // do something with the event
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context,
            JobExecutionException jobException) {
        //JobKey key = context.getJobDetail().getKey();
        // do something with the event
        //System.out.println("Job was executed!");

        //send an email
        Date d = new Date();
        String message = "Job was executed at " + d.toString() + "\n";
        //Returns the result (if any) that the Job set before its execution completed (the type of object set as the result is entirely up to the particular job).
        //The result itself is meaningless to Quartz, but may be informative to JobListeners or TriggerListeners that are watching the job's execution. 
        message += "Result: " + context.getResult() + "\n";
        //Get the unique Id that identifies this particular firing instance of the trigger that triggered this job execution. It is unique to this JobExecutionContext instance as well. 
        message += "Fire Instance Id: " + context.getFireInstanceId() + "\n";
        //Get a handle to the Calendar referenced by the Trigger instance that fired the Job. 
        message += "Calendar: " + (context.getCalendar() != null ? context.getCalendar().getDescription() : "") + "\n";
        //The actual time the trigger fired. For instance the scheduled time may have been 10:00:00 but the actual fire time may have been 10:00:03 if the scheduler was too busy. 
        message += "Fire Time: " + context.getFireTime() + "\n";
        //the job name
        message += "Job Name: " + context.getJobDetail().getKey().getName() + "\n";
        //the job group
        message += "Job Group: " + context.getJobDetail().getKey().getGroup() + "\n";
        //The amount of time the job ran for (in milliseconds). The returned value will be -1 until the job has actually completed (or thrown an exception), and is therefore generally only useful to JobListeners and TriggerListeners.
        message += "RunTime: " + context.getJobRunTime() + "\n";
        //the next fire time
        message += "Next Fire Time: " + context.getNextFireTime().toString() + "\n";
        //the previous fire time
        message += "Previous Fire Time: " + context.getPreviousFireTime().toString() + "\n";
        //refire count
        message += "Refire Count: " + context.getRefireCount() + "\n";

        //job data map
        message += "Job data map: \n";
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
            message += entry.getKey() + " = " + entry.getValue() + "\n";
        }
        Mail.sendMail("SCE notification", message, email, null, null);
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        // do something with the event
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
