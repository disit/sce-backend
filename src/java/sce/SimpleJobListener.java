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

import org.quartz.listeners.JobListenerSupport;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/* 
 * A helpful abstract base class for implementors of JobListener
 * The methods in this class are empty so you only need to override the subset for the JobListener events you care about
 * You are required to implement JobListener.getName() to return the unique name of your JobListener
 */
public class SimpleJobListener extends JobListenerSupport {

    private final String listenerName;

    public SimpleJobListener(String listenerName) {
        this.listenerName = listenerName;
    }

    @Override
    public String getName() {
        return this.listenerName;
    }

    @Override
    // called by the Scheduler when a JobDetail was about to be executed (an associated Trigger has occurred), but a TriggerListener vetoed its execution
    public void jobExecutionVetoed(JobExecutionContext context) {
    }

    @Override
    // called by the Scheduler when a JobDetail is about to be executed (an associated Trigger has occurred)
    public void jobToBeExecuted(JobExecutionContext context) {
    }

    @Override
    // called by the Scheduler after a JobDetail has been executed, and be for the associated Trigger's triggered(xx) method has been called
    public void jobWasExecuted(JobExecutionContext context,
            JobExecutionException jobException) {
    }
}
