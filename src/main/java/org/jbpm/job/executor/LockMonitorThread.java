package org.jbpm.job.executor;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.db.JobSession;
import org.jbpm.job.Job;
import org.jbpm.persistence.JbpmPersistenceException;
import org.jbpm.svc.Services;

public class LockMonitorThread extends Thread {
  
  JbpmConfiguration jbpmConfiguration;
  int lockMonitorInterval;
  int maxLockTime;
  int lockBufferTime;

  boolean isActive = true;

  public LockMonitorThread(JbpmConfiguration jbpmConfiguration, int lockMonitorInterval, int maxLockTime, int lockBufferTime) {
    this.jbpmConfiguration = jbpmConfiguration;
    this.lockMonitorInterval = lockMonitorInterval;
    this.maxLockTime = maxLockTime;
    this.lockBufferTime = lockBufferTime;
  }

  public void run() {
    try {
      while (isActive) {
        try {
          unlockOverdueJobs();
          if ( (isActive) 
               && (lockMonitorInterval>0)
             ) {
            sleep(lockMonitorInterval);
          }
        } catch (InterruptedException e) {
          log.info("lock monitor thread '"+getName()+"' got interrupted");
        } catch (Exception e) {
          log.error("exception in lock monitor thread. waiting "+lockMonitorInterval+" milliseconds", e);
          try {
            sleep(lockMonitorInterval);
          } catch (InterruptedException e2) {
            log.debug("delay after exception got interrupted", e2);
          }
        }
      }
    } catch (Exception e) {
      log.error("exception in lock monitor thread", e);
    } finally {
      log.info(getName()+" leaves cyberspace");
    }
  }

    
  protected void unlockOverdueJobs() {
    JbpmContext jbpmContext = jbpmConfiguration.createJbpmContext();
    try {
      JobSession jobSession = jbpmContext.getJobSession();
      
      Date treshold = new Date(System.currentTimeMillis()-maxLockTime-lockBufferTime);
      List jobsWithOverdueLockTime = jobSession.findJobsWithOverdueLockTime(treshold);
      Iterator iter = jobsWithOverdueLockTime.iterator();
      while (iter.hasNext()) {
        Job job = (Job) iter.next();
        // unlock
        log.debug("unlocking "+job+ " owned by thread "+job.getLockOwner());
        job.setLockOwner(null);
        job.setLockTime(null);
        jobSession.saveJob(job);
      }

    } finally {
      try {
        jbpmContext.close();
      } catch (JbpmPersistenceException e) {
        // if this is a stale object exception, keep it quiet
        if (Services.isCausedByStaleState(e)) {
          log.debug("optimistic locking failed, couldn't unlock overdue jobs");
        } else {
          throw e;
        }
      }
    }
  }

  /**
   * @deprecated As of jBPM 3.2.3, replaced by {@link #deactivate()}
   */
  public void setActive(boolean isActive) {
    if (isActive == false) 
      deactivate();
  }

  /**
   * Indicates that this thread should stop running.
   * Execution will cease shortly afterwards.
   */
  public void deactivate() {
    if (isActive) {
      isActive = false;
      interrupt();      
    }
  }

  private static Log log = LogFactory.getLog(LockMonitorThread.class);
}
