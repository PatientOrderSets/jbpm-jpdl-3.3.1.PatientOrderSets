/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jbpm.db;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.jbpm.JbpmException;
import org.jbpm.graph.def.Action;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.graph.exe.Token;
import org.jbpm.job.Job;
import org.jbpm.job.Timer;

public class JobSession {

  private Session session;

  public JobSession(Session session) {
    this.session = session;
  }

  public Job getFirstAcquirableJob(String lockOwner) {
    Job job = null;
    try {
      Query query = session.getNamedQuery("JobSession.getFirstAcquirableJob");
      query.setString("lockOwner", lockOwner);
      query.setTimestamp("now", new Date());
      query.setMaxResults(1);
      job = (Job) query.uniqueResult();

    } catch (Exception e) {
      log.error(e);
      throw new JbpmException("couldn't get acquirable jobs", e);
    }
    return job;
  }

  public List findExclusiveJobs(String lockOwner, ProcessInstance processInstance) {
    List jobs = null;
    try {
      Query query = session.getNamedQuery("JobSession.findExclusiveJobs");
      query.setString("lockOwner", lockOwner);
      query.setTimestamp("now", new Date());
      query.setParameter("processInstance", processInstance);
      jobs = query.list();

    } catch (Exception e) {
      log.error(e);
      throw new JbpmException("couldn't find exclusive jobs for thread '"+lockOwner+"' and process instance '"+processInstance+"'", e);
    }
    return jobs;
  }
  
  /**
   * find all jobs
   */
  public List<Job> findJobsByToken(Token token) {
    try {
      Query query = session.getNamedQuery("JobSession.findJobsByToken");
      query.setParameter("token", token);
      List<Job> jobs = query.list();
      return jobs;
    } catch (Exception e) {
      throw new JbpmException("couldn't find jobs for token '"+token+"'", e);
    }
  }

  public Job getFirstDueJob(String lockOwner, Collection jobIdsToIgnore) {
    Job job = null;
    try {
      Query query = null;
      if ( (jobIdsToIgnore==null)
           || (jobIdsToIgnore.isEmpty() )
         ) {
        query = session.getNamedQuery("JobSession.getFirstDueJob");
        query.setString("lockOwner", lockOwner);
        
      } else {
        query = session.getNamedQuery("JobSession.getFirstDueJobExlcMonitoredJobs");
        query.setString("lockOwner", lockOwner);
        query.setParameterList("jobIdsToIgnore", jobIdsToIgnore);
        
      }
      query.setMaxResults(1);
      job = (Job) query.uniqueResult();

    } catch (Exception e) {
      log.error(e);
      throw new JbpmException("couldn't get acquirable jobs", e);
    }
    return job;
  }

  public void saveJob(Job job) {
    session.saveOrUpdate(job);
    if (job instanceof Timer) {
      Timer timer = (Timer) job;
      Action action = timer.getAction();
      if (action != null && !session.contains(action)) {
        log.debug("cascading timer save to action");
        session.save(action);
      }
    }
  }

  public void deleteJob(Job job) {
    session.delete(job);
  }

  public Job loadJob(long jobId) {
    try {
      return (Job) session.load(Job.class, new Long(jobId));
    } catch (Exception e) {
      log.error(e);
      throw new JbpmException("couldn't load job '"+jobId+"'", e);
    }
  }

  public Timer loadTimer(long timerId) {
    try {
      return (Timer) session.load(Timer.class, new Long(timerId));
    } catch (Exception e) {
      log.error(e);
      throw new JbpmException("couldn't load timer " + timerId, e);
    }
  }

  public List loadJobs(long[] jobIds) {
    int jobCount = jobIds.length;
    Long[] jobs = new Long[jobCount];
    for (int i = 0; i < jobCount; i++) {
      jobs[i] = new Long(jobIds[i]);
    }
    return session.createCriteria(Job.class)
      .add(Restrictions.in("id", jobs))
      .list();
  }

  public Job getJob(long jobId) {
    try {
      return (Job) session.get(Job.class, new Long(jobId));
    } catch (Exception e) {
      log.error(e);
      throw new JbpmException("couldn't get job '"+jobId+"'", e);
    }
  }

  public void suspendJobs(Token token) {
    try {
      Query query = session.getNamedQuery("JobSession.suspendJobs");
      query.setParameter("token", token);
      query.executeUpdate();

    } catch (Exception e) {
      log.error(e);
      throw new JbpmException("couldn't suspend jobs for "+token, e);
    }
  }

  public void resumeJobs(Token token) {
    try {
      Query query = session.getNamedQuery("JobSession.resumeJobs");
      query.setParameter("token", token);
      query.executeUpdate();

    } catch (Exception e) {
      log.error(e);
      throw new JbpmException("couldn't resume jobs for "+token, e);
    }
  }

  public void deleteTimersByName(String name, Token token) {
    try {
      log.debug("deleting timers by name '" + name + "' for " + token);
      Query query = session.getNamedQuery("JobSession.deleteTimersByName");
      query.setString("name", name);
      query.setParameter("token", token);
      int entityCount = query.executeUpdate();
      log.debug(entityCount + " timers by name '" + name + "' for " + token + " were deleted");
    } catch (Exception e) {
      log.error(e);
      throw new JbpmException("couldn't delete timers by name '" + name + "' for " + token, e);
    }
  }

  public void deleteJobsForProcessInstance(ProcessInstance processInstance) {
    log.debug("deleting timers for "+processInstance);
    Query query = session.getNamedQuery("JobSession.deleteTimersForProcessInstance");
    query.setParameter("processInstance", processInstance);
    int entityCount = query.executeUpdate();
    log.debug(entityCount+" remaining timers for "+processInstance+" were deleted");

    log.debug("deleting execute-node-jobs for "+processInstance);
    query = session.getNamedQuery("JobSession.deleteExecuteNodeJobsForProcessInstance");
    query.setParameter("processInstance", processInstance);
    entityCount = query.executeUpdate();
    log.debug(entityCount+" remaining execute-node-jobs for "+processInstance+" were deleted");
  }


  public List findJobsWithOverdueLockTime(Date treshold) {
    Query query = session.getNamedQuery("JobSession.findJobsWithOverdueLockTime");
    query.setDate("now", treshold);
    return query.list();
  }

  private static Log log = LogFactory.getLog(JobSession.class);
}
