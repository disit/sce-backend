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

// class to log the scheduler status (to be used with ScheduledExecutorService in Main.java)
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileStore;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Date;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerMetaData;
import static sce.LoggingTriggerHistoryPluginCustom.getIpAddress;

public class SchedulerLoggerStatus implements Runnable {

    private static String ip = null;
    private final String url;
    private final String username;
    private final String password;
    private final Scheduler sched;

    public SchedulerLoggerStatus(Scheduler sched, String url, String username, String password) {
        this.sched = sched;
        this.url = url;
        this.username = username;
        this.password = password;
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
        }
    }

    @Override
    public void run() {
        //log node status to database 
        logToDatabase(getSchedulerStatus());
    }

    public void logToDatabase(Object[] args) {
        try {
            Connection conn = DriverManager.getConnection(this.url, this.username, this.password);
            try (PreparedStatement preparedStatement = conn.prepareStatement("INSERT INTO quartz.QRTZ_NODES (JOBS_EXECUTED, SCHEDULER_NAME, RUNNING_SINCE, CLUSTERED, SCHEDULER_INSTANCE_ID, PERSISTENCE, REMOTE_SCHEDULER, CURRENTLY_EXECUTING_JOBS, CPU_LOAD_JVM, SYSTEM_LOAD_AVERAGE, OPERATING_SYSTEM_VERSION, COMMITTED_VIRTUAL_MEMORY, OPERATING_SYSTEM_NAME, FREE_SWAP_SPACE, PROCESS_CPU_TIME, TOTAL_PHYSICAL_MEMORY, NUMBER_OF_PROCESSORS, FREE_PHYSICAL_MEMORY, CPU_LOAD, OPERATING_SYSTEM_ARCHITECTURE, TOTAL_SWAP_SPACE, TOTAL_DISK_SPACE, UNALLOCATED_DISK_SPACE, USABLE_DISK_SPACE, IS_SCHEDULER_STANDBY, IS_SCHEDULER_SHUTDOWN, IS_SCHEDULER_STARTED, IP_ADDRESS) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof String) {
                        preparedStatement.setString(i + 1, (String) args[i]);
                    } else if (args[i] instanceof Date) {
                        preparedStatement.setTimestamp(i + 1, new java.sql.Timestamp(((Date) args[i]).getTime()));
                    }
                }
                preparedStatement.setInt(args.length + 1, sched.isInStandbyMode() ? 1 : 0);
                preparedStatement.setInt(args.length + 2, sched.isShutdown() ? 1 : 0);
                preparedStatement.setInt(args.length + 3, sched.isStarted() ? 1 : 0);
                preparedStatement.setString(args.length + 4, getIpAddress());
                preparedStatement.executeUpdate();
            } catch (SQLException ex) {
                Logger.getLogger(SchedulerLoggerStatus.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SchedulerException ex) {
                Logger.getLogger(SchedulerLoggerStatus.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if (!conn.isClosed()) {
                    conn.close();
                }
            }
        } catch (SQLException ex) {
            Logger.getLogger(SchedulerLoggerStatus.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //get the scheduler status
    public Object[] getSchedulerStatus() {
        try {
            SchedulerMetaData schedulerMetadata = this.sched.getMetaData();
            OperatingSystemMXBean omx = ManagementFactory.getOperatingSystemMXBean();
            com.sun.management.OperatingSystemMXBean osMxBean = null;
            if (omx instanceof com.sun.management.OperatingSystemMXBean) {
                osMxBean = (com.sun.management.OperatingSystemMXBean) omx;

                /**
                 * *******SCHEDULER STATUS********
                 */
                //JOBS_EXECUTED
                String obj1 = Integer.toString(schedulerMetadata.getNumberOfJobsExecuted());

                //SCHEDULER_NAME
                String obj2 = schedulerMetadata.getSchedulerName();

                //RUNNING_SINCE
                Date obj3 = schedulerMetadata.getRunningSince();

                //CLUSTERED
                String obj4 = schedulerMetadata.isJobStoreClustered() ? "1" : "0";

                //SCHEDULER_INSTANCE_ID
                String obj5 = schedulerMetadata.getSchedulerInstanceId();

                //PERSISTENCE
                String obj6 = schedulerMetadata.isJobStoreSupportsPersistence() ? "1" : "0";

                //REMOTE_SCHEDULER
                String obj7 = schedulerMetadata.isSchedulerRemote() ? "1" : "0";

                //CURRENTLY_EXECUTING_JOBS
                String obj8 = Integer.toString(getCurrentlyExecutingNumberOfJobs());

                /**
                 * *******SYSTEM STATUS********
                 */
                //CPU_LOAD_JVM
                String obj9 = Double.toString(osMxBean.getProcessCpuLoad());

                //add SYSTEM_LOAD_AVERAGE
                String obj10 = Double.toString(osMxBean.getSystemLoadAverage());

                //add OPERATING_SYSTEM_VERSION
                String obj11 = osMxBean.getVersion();

                //add COMMITTED_VIRTUAL_MEMORY
                String obj12 = Long.toString(osMxBean.getCommittedVirtualMemorySize());

                //add OPERATING_SYSTEM_NAME
                String obj13 = osMxBean.getName();

                //add FREE_SWAP_SPACE
                String obj14 = Long.toString(osMxBean.getFreeSwapSpaceSize());

                //add PROCESS_CPU_TIME
                String obj15 = Long.toString(osMxBean.getProcessCpuTime());

                //add TOTAL_PHYSICAL_MEMORY
                String obj16 = Double.toString(osMxBean.getTotalPhysicalMemorySize());

                //add NUMBER_OF_PROCESSORS
                String obj17 = Integer.toString(osMxBean.getAvailableProcessors());

                //add FREE_PHYSICAL_MEMORY
                String obj18 = Long.toString(osMxBean.getFreePhysicalMemorySize());

                //add CPU_LOAD
                String obj19 = Double.toString(osMxBean.getSystemCpuLoad());

                //add OPERATING_SYSTEM_ARCHITECTURE
                String obj20 = osMxBean.getArch();

                //add TOTAL_SWAP_SPACE
                String obj21 = Double.toString(osMxBean.getTotalSwapSpaceSize());

                //add TOTAL DISK SPACE
                String obj22 = Long.toString(getTotalSpace());

                //add UNALLOCATED DISK SPACE
                String obj23 = Long.toString(getUnallocatedSpace());

                //add USABLE DISK SPACE
                String obj24 = Long.toString(getUsableSpace());

                Object[] args = {obj1, obj2, obj3, obj4, obj5, obj6, obj7, obj8, obj9, obj10, obj11, obj12, obj13, obj14, obj15, obj16, obj17, obj18, obj19, obj20, obj21, obj22, obj23, obj24};

                return args;
            } else {
                return null;
            }
        } catch (SchedulerException e) {
            e.printStackTrace(System.out);
            return null;
        }
    }

    // get scheduler's executing nuber of jobs
    public int getCurrentlyExecutingNumberOfJobs() {
        try {
            return sched.getCurrentlyExecutingJobs().size();
        } catch (SchedulerException e) {
            return 0;
        }
    }

    //get the ipv4 addresses, semicolon separated, of the current network interface (excluding 127.0.0.1)
    public static String getIpAddress() {
        if (ip == null) {
            try {
                for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                    NetworkInterface intf = (NetworkInterface) en.nextElement();
                    for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        InetAddress inetAddress = (InetAddress) enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                            String ipAddress = inetAddress.getHostAddress().toString();
                            ip = (ip == null ? "" : ip + ";") + ipAddress;
                        }
                    }
                }
            } catch (SocketException e) {
                return "";
            }
        }
        return ip;
    }

    //Returns the size, in bytes, of the file store
    public long getTotalSpace() {
        long result = 0;
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            try {
                result += store.getTotalSpace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    //Returns the number of unallocated bytes in the file store
    public long getUnallocatedSpace() {
        long result = 0;
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            try {
                result += store.getUnallocatedSpace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    //Returns the number of bytes available to this Java virtual machine on the file store
    public long getUsableSpace() {
        long result = 0;
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            try {
                result += store.getUsableSpace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    //get human redable bytes, not used
    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
