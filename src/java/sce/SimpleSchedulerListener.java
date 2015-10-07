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

import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.listeners.SchedulerListenerSupport;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

/* 
 * A helpful abstract base class for implementors of SchedulerListener
 * The methods in this class are empty so you only need to override the subset for the SchedulerListener events you care about 
 */
public class SimpleSchedulerListener extends SchedulerListenerSupport {

    @Override
    // called by the Scheduler when a JobDetail has been added
    public void jobAdded(JobDetail jobDetail) {
    }

    @Override
    // called by the Scheduler when a JobDetail has been deleted
    public void jobDeleted(JobKey jobKey) {
    }

    @Override
    // called by the Scheduler when a JobDetail has been paused
    public void jobPaused(JobKey jobKey) {
    }

    @Override
    // called by the Scheduler when a JobDetail has been un-paused
    public void jobResumed(JobKey jobKey) {
    }

    @Override
    // called by the Scheduler when a JobDetail is scheduled
    public void jobScheduled(Trigger trigger) {
    }

    @Override
    // called by the Scheduler when a group of JobDetails has been paused
    public void jobsPaused(String jobGroup) {
    }

    @Override
    // called by the Scheduler when a group of JobDetails has been un-paused
    public void jobsResumed(String jobGroup) {
    }

    @Override
    // called by the Scheduler when a JobDetail is unscheduled
    public void jobUnscheduled(TriggerKey triggerKey) {
    }

    @Override
    // called by the Scheduler when a serious error has occurred within
    // the scheduler such as repeated failures in the JobStore, or the 
    // inability to instantiate a Job instance when its Trigger has fired
    public void schedulerError(String msg, SchedulerException cause) {
    }

    @Override
    // called by the Scheduler to inform the listener that it has move to standby mode
    public void schedulerInStandbyMode() {
    }

    @Override
    // called by the Scheduler to inform the listener that it has shutdown
    public void schedulerShutdown() {
    }

    @Override
    // called by the Scheduler to inform the listener that it has begun the shutdown sequence
    public void schedulerShuttingdown() {
    }

    @Override
    // called by the Scheduler to inform the listener that it has started
    public void schedulerStarted() {
    }

    @Override
    // called by the Scheduler to inform the listener that it is starting
    public void schedulerStarting() {
    }

    @Override
    // called by the Scheduler to inform the listener that all jobs, triggers and calendars were deleted
    public void schedulingDataCleared() {
    }

    @Override
    // called by the Scheduler when a Trigger has reached the condition in which it will never fire again
    public void triggerFinalized(Trigger trigger) {
    }

    @Override
    // called by the Scheduler when a Trigger has been paused
    public void triggerPaused(TriggerKey triggerKey) {
    }

    @Override
    // called by the Scheduler when a Trigger has been un-paused
    public void triggerResumed(TriggerKey triggerKey) {
    }

    @Override
    // called by the Scheduler when a group of Triggers has been paused
    public void triggersPaused(String triggerGroup) {
    }

    @Override
    // called by the Scheduler when a group of Triggers has been un-paused
    public void triggersResumed(String triggerGroup) {
    }
}
