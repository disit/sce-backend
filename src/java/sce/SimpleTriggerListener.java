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

import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.listeners.TriggerListenerSupport;

/* 
 * A helpful abstract base class for implementors of TriggerListener
 * The methods in this class are empty so you only need to override the subset for the TriggerListener events you care about
 * You are required to implement TriggerListener.getName() to return the unique name of your TriggerListener
 * http://quartz-scheduler.org/generated/2.2.1/html/qs-all/#page/Quartz_Scheduler_Documentation_Set%2Fto-lsr_working_with_tiggerlisteners_and_joblisteners.html
 */
public class SimpleTriggerListener extends TriggerListenerSupport {

    private final String listenerName;
    
    public SimpleTriggerListener(String listenerName) {
        this.listenerName = listenerName;
    }

    @Override
    // get the Logger for this class's category
    public String getName() {
        return this.listenerName;
    }

    @Override
    // called by the Scheduler when a Trigger has fired, it's associated JobDetail has been executed, and its triggered(xx) method has been called
    public void triggerComplete(Trigger trigger, JobExecutionContext context, Trigger.CompletedExecutionInstruction triggerInstructionCode) {
    }

    @Override

    // called by the Scheduler when a Trigger has fired, and its associated JobDetail is about to be executed
    public void triggerFired(Trigger trigger, JobExecutionContext context) {
    }

    @Override
    // called by the Scheduler when a Trigger has misfired
    public void triggerMisfired(Trigger trigger) {
    }

    @Override
    // called by the Scheduler when a Trigger has fired, and its associated JobDetail is about to be executed
    public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
        return false;
    }
}
