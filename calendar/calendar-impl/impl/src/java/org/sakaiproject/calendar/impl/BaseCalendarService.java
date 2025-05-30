/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007, 2008, 2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.calendar.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.*;
import net.fortuna.ical4j.util.MapTimeZoneCache;

import org.apache.commons.lang3.StringUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.sakaiproject.alias.api.Alias;
import org.sakaiproject.alias.api.AliasService;
import org.sakaiproject.authz.api.*;
import org.sakaiproject.calendar.api.Calendar;
import org.sakaiproject.calendar.api.*;
import org.sakaiproject.calendar.api.CalendarEvent.EventAccess;
import org.sakaiproject.calendar.api.ExternalCalendarSubscriptionService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.entity.api.*;
import org.sakaiproject.event.api.*;
import org.sakaiproject.exception.*;
import org.sakaiproject.id.api.IdManager;
import org.sakaiproject.javax.Filter;
import org.sakaiproject.memory.api.Cache;
import org.sakaiproject.memory.api.MemoryService;
import org.sakaiproject.memory.api.SimpleConfiguration;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.lti.api.LTIService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeRange;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.api.SessionBindingEvent;
import org.sakaiproject.tool.api.SessionBindingListener;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.*;
import org.sakaiproject.util.api.LinkMigrationHelper;

import org.sakaiproject.assignment.api.AssignmentServiceConstants;
import org.sakaiproject.api.app.messageforums.DiscussionForumService;
import org.sakaiproject.samigo.util.SamigoConstants;

import org.sakaiproject.util.MergeConfig;

/**
 * <p>
 * BaseCalendarService is an base implementation of the CalendarService. Extension classes implement object creation, access and storage.
 * </p>
 */
@Slf4j
public abstract class BaseCalendarService implements CalendarService, DoubleStorageUser, ContextObserver, EntityTransferrer, SAXEntityReader, Observer
{
	/** The initial portion of a relative access point URL. */
	protected String m_relativeAccessPoint = null;

	/** A Storage object for access to calendars and events. */
	protected Storage m_storage = null;

	/** DELIMETER used to separate the list of custom fields for this calendar. */
	private final static String ADDFIELDS_DELIMITER = "_,_";
	
	/** Security lock / event root for generic message events to make it a mail event. */
	public static final String SECURE_SCHEDULE_ROOT = "calendar.";

	/** SAK-29003 Google needs .ics at end of URL **/
	public static final String ICAL_EXTENSION = ".ics";
	
	private static final String EVENT_URL_PATTERN = "%s?eventReference=%s&panel=Main&sakai_action=doDescription&sakai.state.reset=true";

   	private DocumentBuilder docBuilder = null;
   
   	private static final ResourceLoader rb = new ResourceLoader("calendar");
   
	@Setter private ContentHostingService contentHostingService;
	@Setter private CourseManagementService courseManagementService;
	@Setter private ExternalCalendarSubscriptionService externalCalendarSubscriptionService;
	@Setter private LTIService ltiService;
	@Setter protected SessionManager sessionManager;
	@Setter protected TimeService timeService;
	@Setter protected ToolManager toolManager;
	@Setter protected UserDirectoryService userDirectoryService;
	@Setter protected UsageSessionService usageSessionService;
	@Setter protected EntityManager entityManager;
	@Setter protected ServerConfigurationService serverConfigurationService;
	@Setter protected AliasService aliasService;
	@Setter protected SiteService siteService;
	@Setter protected MemoryService memoryService;
	@Setter protected IdManager idManager;
	@Setter protected SecurityService securityService;
	@Setter protected AuthzGroupService authzGroupService;
	@Setter protected FunctionManager functionManager;
	@Setter protected EventTrackingService eventTrackingService;
	@Setter protected OpaqueUrlDao opaqueUrlDao;
	@Setter protected LinkMigrationHelper linkMigrationHelper;
   	private PDFExportService pdfExportService;

	private GroupComparator groupComparator = new GroupComparator();
	
	public static final String UI_SERVICE = "ui.service";
	
	public static final String SAKAI = "Sakai";
	
	private Cache<String, Calendar> cache = null;
	
	/**
	 * Access this service from the inner classes.
	 */
	protected BaseCalendarService service()
	{
		return this;
	}

	/**
	 * Construct a Storage object.
	 * 
	 * @return The new storage object.
	 */
	protected abstract Storage newStorage();

	/**
	 * Access the partial URL that forms the root of calendar URLs.
	 * 
	 * @param relative
	 *        if true, form within the access path only (i.e. starting with /content)
	 * @return the partial URL that forms the root of calendar URLs.
	 */
	protected String getAccessPoint(boolean relative)
	{
		return (relative ? "" : serverConfigurationService.getAccessUrl()) + m_relativeAccessPoint;

	} // getAccessPoint

	/**
	 * Check security permission.
	 * 
	 * @param lock
	 *        The lock id string.
	 * @param reference
	 *        The resource's reference string, or null if no resource is involved.
	 * @return true if permitted, false if not.
	 */
	protected boolean unlockCheck(String lock, String reference)
	{
		if (lock.equals(AUTH_READ_CALENDAR) &&  getExportEnabled(reference))
			return true;

		return securityService.unlock(lock, reference);
	} // unlockCheck

	/**
	 * Check security permission.
	 * 
	 * @param lock
	 *        The lock id string.
	 * @param reference
	 *        The resource's reference string, or null if no resource is involved.
	 * @exception PermissionException
	 *            thrown if the user does not have access
	 */
	protected void unlock(String lock, String reference) throws PermissionException
	{
		if (!unlockCheck(lock, reference))
			throw new PermissionException(sessionManager.getCurrentSessionUserId(), lock, reference);

	} // unlock

	@Override
	public String calendarReference(String context, String id)
	{
		return getAccessPoint(true) + Entity.SEPARATOR + REF_TYPE_CALENDAR + Entity.SEPARATOR + context + Entity.SEPARATOR + id;

	} // calendarReference

	public String calendarPdfReference(String context, String id, int scheduleType, String timeRangeString, String userName, boolean reverseOrder) {
		return getAccessPoint(true) + Entity.SEPARATOR + REF_TYPE_CALENDAR_PDF + Entity.SEPARATOR + context + Entity.SEPARATOR + id
				+ "?" + SCHEDULE_TYPE_PARAMETER_NAME + "=" + Validator.escapeHtml(Integer.valueOf(scheduleType).toString()) + "&"
				+ TIME_RANGE_PARAMETER_NAME + "=" + timeRangeString + "&"
				+ Validator.escapeHtml(USER_NAME_PARAMETER_NAME) + "=" + Validator.escapeUrl(userName) + "&"
				+ ORDER_EVENTS_PARAMETER_NAME + "=" + reverseOrder;
	}

	@Override
	public String calendarICalReference(Reference ref)
	{
      String context = ref.getContext();
      String id = ref.getId();
      String alias = null;
      List aliasList =  aliasService.getAliases( ref.getReference() );
      
      if ( ! aliasList.isEmpty() )
         alias = ((Alias)aliasList.get(0)).getId();
         
      if ( alias != null)
   		return getAccessPoint(true) + Entity.SEPARATOR + REF_TYPE_CALENDAR_ICAL + Entity.SEPARATOR + alias;
      else
   		return getAccessPoint(true) + Entity.SEPARATOR + REF_TYPE_CALENDAR_ICAL + Entity.SEPARATOR + context + Entity.SEPARATOR + id;
	}
   
	@Override
	public String calendarSubscriptionReference(String context, String id)
	{
      return getAccessPoint(true) + Entity.SEPARATOR + REF_TYPE_CALENDAR_SUBSCRIPTION + Entity.SEPARATOR + context + Entity.SEPARATOR + id;
	}
	
	@Override
	public boolean getExportEnabled(String ref)
	{
		Calendar cal = findCalendar(ref);
		if ( cal == null )
			return false;
		else
			return cal.getExportEnabled();
	}
	
	@Override
	public void setExportEnabled(String ref, boolean enable)
	{
		try
		{
			CalendarEdit cal = editCalendar(ref);
			cal.setExportEnabled(enable);
			commitCalendar(cal);
			
			//Update the cache object if exists
			if(cache != null) {
				if(cache.containsKey(ref)) {
					cache.put(ref,cal);
				}
			}
		}
		catch ( Exception e)
		{
			log.warn("setExportEnabled(): ", e);
		}
	}
	
	/**
	 * Access the internal reference which can be used to access the event from within the system.
	 * 
	 * @param context
	 *        The context.
	 * @param calendarId
	 *        The calendar id.
	 * @param id
	 *        The event id.
	 * @return The the internal reference which can be used to access the event from within the system.
	 */
	public String eventReference(String context, String calendarId, String id)
	{
		return getAccessPoint(true) + Entity.SEPARATOR + REF_TYPE_EVENT + Entity.SEPARATOR + context + Entity.SEPARATOR
				+ calendarId + Entity.SEPARATOR + id;

	} // eventReference
	
	/**
	 * Access the internal reference which can be used to access the subscripted event from within the system.
	 * 
	 * @param context
	 *        The context.
	 * @param calendarId
	 *        The calendar id.
	 * @param id
	 *        The event id.
	 * @return The the internal reference which can be used to access the subscripted event from within the system.
	 */
	public String eventSubscriptionReference(String context, String calendarId, String id)
	{
		return getAccessPoint(true) + Entity.SEPARATOR + REF_TYPE_EVENT_SUBSCRIPTION + Entity.SEPARATOR + context + Entity.SEPARATOR
				+ calendarId + Entity.SEPARATOR + id;

	} // eventSubscriptionReference

	@Override
	public CalendarEventVector getEvents(List references, TimeRange range, boolean reverseOrder) {

		CalendarEventVector calendarEventVector = null;

		if (references != null) {
			if (range == null) {
				range = getOneYearTimeRange();
			}
			List allEvents = new ArrayList();

			Iterator it = references.iterator();

			// Add the events for each calendar in our list.
			while (it.hasNext())
			{
				String calendarReference = (String) it.next();
				Calendar calendarObj = null;

				try
				{
					calendarObj = getCalendar(calendarReference);
				}

				catch (IdUnusedException e)
				{
					continue;
				}

				catch (PermissionException e)
				{
					continue;
				}

				if (calendarObj != null)
				{
					Iterator calEvent = null;

					try
					{
						calEvent = calendarObj.getEvents(range, null).iterator();
					}

					catch (PermissionException e1)
					{
						continue;
					}

					allEvents.addAll(new CalendarEventVector(calEvent));
				}
			}

			// Do a sort since each of the events implements the Comparable interface.
			Collections.sort(allEvents);
			if (reverseOrder) {
				Collections.reverse(allEvents);
			}

			// Build up a CalendarEventVector and return it.
			calendarEventVector = new CalendarEventVector(allEvents.iterator());
		}

		return calendarEventVector;
	}

	private List<CalendarEvent> getEvents(List<String> references, TimeRange range, boolean reverseOrder, Integer limit) {

		List<CalendarEvent> allEvents = new ArrayList();

		if (references != null) {
			if (range == null) {
				range = getOneYearTimeRange();
			}
			for (String ref : references) {
				try {
					Calendar calendar = getCalendar(ref);

					try {
						allEvents.addAll(calendar.getEvents(range, null, limit));
					} catch (PermissionException e1) {
						continue;
					}
				} catch (IdUnusedException | PermissionException e) {
					continue;
				}
			}

			if (reverseOrder) {
				Collections.reverse(allEvents);
			} else {
				Collections.sort(allEvents);
			}
		}

		return allEvents;
	}
	
	@Override
	public CalendarEventVector getEvents(List references, TimeRange range)
	{
		return this.getEvents(references, range, false);
	}

	public List<CalendarEvent> getFilteredEvents(Map<EventFilterKey, Object> options) {

		if (options == null) options = Collections.emptyMap();

		List<String> allRefs = new ArrayList<>();

		if (options.containsKey(EventFilterKey.SITE)) {
			// A single site has been requested
			allRefs.add(calendarReference((String) options.get(EventFilterKey.SITE), SiteService.MAIN_CONTAINER));
		} else {
			// First, grab calendar references for all the project sites
			allRefs = siteService.getSites(SiteService.SelectionType.ACCESS, "project", null, null, null, null)
				.stream()
				.map(s -> calendarReference(s.getId(), SiteService.MAIN_CONTAINER))
				.collect(Collectors.toList());

			Map<String, String> propCrit = new HashMap<>();

			// Now grab references of sites in the current academic sessions
			allRefs.addAll(courseManagementService.getCurrentAcademicSessions().stream().map(as -> {

					propCrit.put(Site.PROP_SITE_TERM, as.getTitle());
					return siteService.getSiteIds(SiteService.SelectionType.ACCESS, "course", null, propCrit, null, null).stream()
						.map(id -> calendarReference(id, SiteService.MAIN_CONTAINER))
						.collect(Collectors.toList());
				}).flatMap(Collection::stream).collect(Collectors.toList()));
		}

		int daysLimit = getUpcomingDaysLimit();
		Instant now = Instant.now();
		Instant end = now.plus(daysLimit, ChronoUnit.DAYS);
		TimeRange range = timeService.newTimeRange(timeService.newTime(now.toEpochMilli()), timeService.newTime(end.toEpochMilli()), true, true);

		Integer eventsLimitPerCalendar = (Integer) options.get(EventFilterKey.LIMIT);
		return new ArrayList<>(getEvents(allRefs, range, false, eventsLimitPerCalendar));
	}

	public int getUpcomingDaysLimit() {
		return serverConfigurationService.getInt("calendar.upcoming_days_limit", 60);
	}

	/**
	* Form a tracking event string based on a security function string.
	* @param secure The security function string.
	* @return The event tracking string.
	*/
	protected String eventId(String secure)
	{
		return SECURE_SCHEDULE_ROOT + secure;

	} // eventId
	
	/**
	 * Access the id generating service and return a unique id.
	 * 
	 * @return a unique id.
	 */
	protected String getUniqueId()
	{
		return idManager.createUuid();
	}

	/** A map of services used in SAX serialization */
	private Map<String, Object> m_services;

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Init and Destroy
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		try
		{
			m_relativeAccessPoint = REFERENCE_ROOT;

			// construct a storage helper and read
			m_storage = newStorage();
			m_storage.open();


			// create DocumentBuilder object needed by printSchedule
			docBuilder =  DocumentBuilderFactory.newInstance().newDocumentBuilder();
			
			log.info("init()");
		}
		catch (Throwable t)
		{
			log.warn("init(): "+t, t);
		}

		// register as an entity producer
		entityManager.registerEntityProducer(this, REFERENCE_ROOT);

		// register functions
		functionManager.registerFunction(AUTH_ADD_CALENDAR, true);
		functionManager.registerFunction(AUTH_REMOVE_CALENDAR_OWN, true);
		functionManager.registerFunction(AUTH_REMOVE_CALENDAR_ANY, true);
		functionManager.registerFunction(AUTH_MODIFY_CALENDAR_OWN, true);
		functionManager.registerFunction(AUTH_MODIFY_CALENDAR_ANY, true);
		functionManager.registerFunction(AUTH_IMPORT_CALENDAR, true);
		functionManager.registerFunction(AUTH_SUBSCRIBE_CALENDAR, true);
		functionManager.registerFunction(AUTH_READ_CALENDAR, true);
		functionManager.registerFunction(AUTH_ALL_GROUPS_CALENDAR, true);
		functionManager.registerFunction(AUTH_OPTIONS_CALENDAR, true);
		functionManager.registerFunction(AUTH_VIEW_AUDIENCE, true);
		
		// setup cache
		SimpleConfiguration cacheConfig = new SimpleConfiguration(0);
		cacheConfig.setStatisticsEnabled(true);
		cache = this.memoryService.createCache("org.sakaiproject.calendar.cache", cacheConfig);
		System.setProperty("net.fortuna.ical4j.timezone.cache.impl", MapTimeZoneCache.class.getName());

		eventTrackingService.addObserver(this);
		pdfExportService = new PDFExportService(timeService, rb);
	}

	/**
	 * Returns to uninitialized state.
	 */
	public void destroy()
	{
		m_storage.close();
		m_storage = null;

		log.info("destroy()");
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * CalendarService implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Add a new calendar. Must commitCalendar() to make official, or cancelCalendar() when done!
	 * 
	 * @param ref
	 *        A reference for the calendar.
	 * @return The newly created calendar.
	 * @exception IdUsedException
	 *            if the id is not unique.
	 * @exception IdInvalidException
	 *            if the id is not made up of valid characters.
	 */
	public CalendarEdit addCalendar(String ref) throws IdUsedException, IdInvalidException
	{
		// check the name's validity
		if (!entityManager.checkReference(ref)) throw new IdInvalidException(ref);

		// check for existance
		if (m_storage.checkCalendar(ref))
		{
			throw new IdUsedException(ref);
		}

		// keep it
		CalendarEdit calendar = m_storage.putCalendar(ref);

		((BaseCalendarEdit) calendar).setEvent(EVENT_CREATE_CALENDAR);

		return calendar;

	} // addCalendar

	/**
	 * check permissions for getCalendar().
	 * 
	 * @param ref
	 *        The calendar reference.
	 * @return true if the user is allowed to getCalendar(calendarId), false if not.
	 */
	public boolean allowGetCalendar(String ref)
	{
		if(REF_TYPE_CALENDAR_SUBSCRIPTION.equals(entityManager.newReference(ref).getSubType()))
			return true;
		return unlockCheck(AUTH_READ_CALENDAR, ref);

	} // allowGetCalendar

	/**
	 * Find the calendar, in cache or info store - cache it if newly found.
	 * 
	 * @param ref
	 *        The calendar reference.
	 * @return The calendar, if found.
	 */
	protected Calendar findCalendar(String ref)
	{
		Calendar calendar = null;
			
		//check cache
		if(cache != null) {
			if(cache.containsKey(ref)) {
				calendar = (Calendar)cache.get(ref);
			}
		}
		
		//if calendar is still null, it's not in the cache, get it from storage and cache it
		if(calendar == null) {
			calendar = m_storage.getCalendar(ref);
			cache.put(ref, calendar);
		}
		
		return calendar;
	} // findCalendar

	/**
	 * Return a specific calendar.
	 * 
	 * @param ref
	 *        The calendar reference.
	 * @return the Calendar that has the specified name.
	 * @exception IdUnusedException
	 *            If this name is not defined for any calendar.
	 * @exception PermissionException
	 *            If the user does not have any permissions to the calendar.
	 */
	public Calendar getCalendar(String ref) throws IdUnusedException, PermissionException {
		return getCalendar(ref, null, null);
	}
	
	public Calendar getCalendar(String ref, String userId, String tzid) throws IdUnusedException, PermissionException
	{
		Reference _ref = entityManager.newReference(ref);
		if(REF_TYPE_CALENDAR_SUBSCRIPTION.equals(_ref.getSubType())) {
			Calendar c = externalCalendarSubscriptionService.getCalendarSubscription(ref, userId, tzid);
			if (c == null) throw new IdUnusedException(ref);
			return c;
		}
			
		Calendar c = findCalendar(ref);
		if (c == null) throw new IdUnusedException(ref);

		// check security (throws if not permitted)
		unlock(AUTH_READ_CALENDAR, ref);

		return c;

	} // getCalendar

	/**
	 * Remove a calendar that is locked for edit.
	 * 
	 * @param calendar
	 *        The calendar to remove.
	 * @exception PermissionException
	 *            if the user does not have permission to remove a calendar.
	 */
	public void removeCalendar(CalendarEdit calendar) throws PermissionException
	{
		// check for closed edit
		if (!calendar.isActiveEdit())
		{
			try
			{
				throw new Exception();
			}
			catch (Exception e)
			{
				log.warn("removeCalendar(): closed CalendarEdit", e);
			}
			return;
		}

		// check security
		unlock(AUTH_REMOVE_CALENDAR_ANY, calendar.getReference());

		m_storage.removeCalendar(calendar);

		// track event
		Event event = eventTrackingService.newEvent(EVENT_REMOVE_CALENDAR, calendar.getReference(), true);
		eventTrackingService.post(event);

		// mark the calendar as removed
		((BaseCalendarEdit) calendar).setRemoved(event);

		// close the edit object
		((BaseCalendarEdit) calendar).closeEdit();

		// remove any realm defined for this resource
		try
		{
			authzGroupService.removeAuthzGroup(authzGroupService.getAuthzGroup(calendar.getReference()));
		}
		catch (AuthzPermissionException e)
		{
			log.warn("removeCalendar: removing realm for : " + calendar.getReference() + " : " + e);
		}
		catch (GroupNotDefinedException gnde)
		{
			log.debug(gnde.getMessage());
		}
		catch (AuthzRealmLockException arle)
		{
			log.warn("GROUP LOCK REGRESSION: {}", arle.getMessage(), arle);
		}

	} // removeCalendar

	/**
	 * check permissions for importing calendar events
	 * 
	 * @param ref
	 *        The calendar reference.
	 * @return true if the user is allowed to import events, false if not.
	 */
	public boolean allowImportCalendar(String ref)
	{
		return unlockCheck(AUTH_IMPORT_CALENDAR, ref);

	} // allowImportCalendar

	/**
	 * check permissions for subscribing external calendars
	 * 
	 * @param ref
	 *        The calendar reference.
	 * @return true if the user is allowed to subscribe external calendars, false if not.
	 */
	public boolean allowSubscribeCalendar(String ref)
	{
		return unlockCheck(AUTH_SUBSCRIBE_CALENDAR, ref);

	} // allowSubscribeCalendar

	/**
	 * check permissions for editCalendar()
	 * 
	 * @param ref
	 *        The calendar reference.
	 * @return true if the user is allowed to update the calendar, false if not.
	 */
	public boolean allowEditCalendar(String ref)
	{
		return unlockCheck(AUTH_MODIFY_CALENDAR_ANY, ref);

	} // allowEditCalendar
   
	/**
	* check permissions for merge()
	* @param ref The calendar reference.
	* @return true if the user is allowed to update the calendar, false if not.
	*/
	public boolean allowMergeCalendar(String ref)
	{
		 String displayMerge = getString("calendar.merge.display", "1");
		 
		 if(displayMerge != null && !displayMerge.equals("1"))
			 return false;     
		 
		 return unlockCheck(AUTH_MODIFY_CALENDAR_ANY, ref);

	} // allowMergeCalendar

	/**
	 * Get a locked calendar object for editing. Must commitCalendar() to make official, or cancelCalendar() or removeCalendar() when done!
	 * 
	 * @param ref
	 *        The calendar reference.
	 * @return A CalendarEdit object for editing.
	 * @exception IdUnusedException
	 *            if not found, or if not an CalendarEdit object
	 * @exception PermissionException
	 *            if the current user does not have permission to mess with this user.
	 * @exception InUseException
	 *            if the Calendar object is locked by someone else.
	 */
	public CalendarEdit editCalendar(String ref) throws IdUnusedException, PermissionException, InUseException
	{
		// check for existance
		if (!m_storage.checkCalendar(ref))
		{
			throw new IdUnusedException(ref);
		}

		// check security (throws if not permitted)
		unlock(AUTH_MODIFY_CALENDAR_ANY, ref);

		// ignore the cache - get the calendar with a lock from the info store
		CalendarEdit edit = m_storage.editCalendar(ref);
		if (edit == null) throw new InUseException(ref);

		((BaseCalendarEdit) edit).setEvent(EVENT_MODIFY_CALENDAR);

		return edit;

	} // editCalendar

	/**
	 * Commit the changes made to a CalendarEdit object, and release the lock. The CalendarEdit is disabled, and not to be used after this call.
	 * 
	 * @param edit
	 *        The CalendarEdit object to commit.
	 */
	public void commitCalendar(CalendarEdit edit)
	{
		// check for closed edit
		if (!edit.isActiveEdit())
		{
			log.warn("commitCalendar(): closed CalendarEdit " + edit.getContext());
			return;
		}

		m_storage.commitCalendar(edit);

		// track event
		Event event = eventTrackingService.newEvent(((BaseCalendarEdit) edit).getEvent(), edit.getReference(), true);
		eventTrackingService.post(event);

		// close the edit object
		((BaseCalendarEdit) edit).closeEdit();

	} // commitCalendar

	/**
	 * Cancel the changes made to a CalendarEdit object, and release the lock. The CalendarEdit is disabled, and not to be used after this call.
	 * 
	 * @param edit
	 *        The CalendarEdit object to commit.
	 */
	public void cancelCalendar(CalendarEdit edit)
	{
		// check for closed edit
		if (!edit.isActiveEdit())
		{
			log.warn("cancelCalendar(): closed CalendarEventEdit " + edit.getContext());
			return;
		}

		// release the edit lock
		m_storage.cancelCalendar(edit);

		// close the edit object
		((BaseCalendarEdit) edit).closeEdit();

	} // cancelCalendar

	@Override
	public RecurrenceRule newRecurrence(String frequency)
	{
		if (frequency.equalsIgnoreCase(DailyRecurrenceRule.FREQ))
		{
			return new DailyRecurrenceRule();
		}
		else if (frequency.equalsIgnoreCase(WeeklyRecurrenceRule.FREQ))
		{
			return new WeeklyRecurrenceRule();
		}
		else if (frequency.equalsIgnoreCase(TThRecurrenceRule.FREQ))
		{
			return new TThRecurrenceRule();
		}
		else if (frequency.equalsIgnoreCase(MWFRecurrenceRule.FREQ))
		{
			return new MWFRecurrenceRule();
		}
		else if (frequency.equalsIgnoreCase(MonthlyRecurrenceRule.FREQ))
		{
			return new MonthlyRecurrenceRule();
		}
		else if (frequency.equalsIgnoreCase(YearlyRecurrenceRule.FREQ))
		{
			return new YearlyRecurrenceRule();
		}
		else if (frequency.equalsIgnoreCase(MWRecurrenceRule.FREQ))
		{
			return new MWRecurrenceRule();
		}
		else if (frequency.equalsIgnoreCase(SMWRecurrenceRule.FREQ))
		{
			return new SMWRecurrenceRule();
		}
		else if (frequency.equalsIgnoreCase(SMTWRecurrenceRule.FREQ))
		{
			return new SMTWRecurrenceRule();
		}
		else if (frequency.equalsIgnoreCase(STTRecurrenceRule.FREQ))
		{
			return new STTRecurrenceRule();
		}
		//add more here

		return null;
	}

	@Override
	public RecurrenceRule newRecurrence(String frequency, int interval)
	{
		if (frequency.equalsIgnoreCase(DailyRecurrenceRule.FREQ))
		{
			return new DailyRecurrenceRule(interval);
		}
		else if (frequency.equalsIgnoreCase(WeeklyRecurrenceRule.FREQ))
		{
			return new WeeklyRecurrenceRule(interval);
		}
		else if (frequency.equalsIgnoreCase(TThRecurrenceRule.FREQ))
		{
			return new TThRecurrenceRule(interval);
		}
		else if (frequency.equalsIgnoreCase(MWFRecurrenceRule.FREQ))
		{
			return new MWFRecurrenceRule(interval);
		}
		else if (frequency.equalsIgnoreCase(MonthlyRecurrenceRule.FREQ))
		{
			return new MonthlyRecurrenceRule(interval);
		}
		else if (frequency.equalsIgnoreCase(YearlyRecurrenceRule.FREQ))
		{
			return new YearlyRecurrenceRule(interval);
		}
		else if (frequency.equalsIgnoreCase(MWRecurrenceRule.FREQ))
		{
			return new MWRecurrenceRule(interval);
		}
		else if (frequency.equalsIgnoreCase(SMWRecurrenceRule.FREQ))
		{
			return new SMWRecurrenceRule(interval);
		}
		else if (frequency.equalsIgnoreCase(SMTWRecurrenceRule.FREQ))
		{
			return new SMTWRecurrenceRule(interval);
		}
		else if (frequency.equalsIgnoreCase(STTRecurrenceRule.FREQ))
		{
			return new STTRecurrenceRule(interval);
		}
		//add more here
	
		return null;
	}

	@Override
	public RecurrenceRule newRecurrence(String frequency, int interval, int count)
	{	
		log.debug("\n"+ frequency +"\nand Internval is \n "+ interval +"count is\n " + count);
		if (frequency.equalsIgnoreCase(DailyRecurrenceRule.FREQ))
		{
			return new DailyRecurrenceRule(interval, count);
		}
		else if (frequency.equalsIgnoreCase(WeeklyRecurrenceRule.FREQ))
		{
			return new WeeklyRecurrenceRule(interval, count);
		}
		else if (frequency.equalsIgnoreCase(TThRecurrenceRule.FREQ))
		{
			return new TThRecurrenceRule(interval,count);
		}
		else if (frequency.equalsIgnoreCase(MWFRecurrenceRule.FREQ))
		{
			return new MWFRecurrenceRule(interval,count);
		} 
		else if (frequency.equalsIgnoreCase(MonthlyRecurrenceRule.FREQ))
		{
			return new MonthlyRecurrenceRule(interval, count);
		}
		else if (frequency.equalsIgnoreCase(YearlyRecurrenceRule.FREQ))
		{
			return new YearlyRecurrenceRule(interval, count);
		}
		else if (frequency.equalsIgnoreCase(MWRecurrenceRule.FREQ))
		{
			return new MWRecurrenceRule(interval, count);
		}
		else if (frequency.equalsIgnoreCase(SMWRecurrenceRule.FREQ))
		{
			return new SMWRecurrenceRule(interval, count);
		}
		else if (frequency.equalsIgnoreCase(SMTWRecurrenceRule.FREQ))
		{
			return new SMTWRecurrenceRule(interval, count);
		}
		else if (frequency.equalsIgnoreCase(STTRecurrenceRule.FREQ))
		{
			return new STTRecurrenceRule(interval, count);
		}
		//add more here

		return null;
	}

	@Override
	public RecurrenceRule newRecurrence(String frequency, int interval, Time until)
	{
		if (frequency.equalsIgnoreCase(DailyRecurrenceRule.FREQ))
		{
			return new DailyRecurrenceRule(interval, until);
		}
		else if (frequency.equalsIgnoreCase(WeeklyRecurrenceRule.FREQ))
		{
			return new WeeklyRecurrenceRule(interval, until);
		}
		else if (frequency.equalsIgnoreCase(TThRecurrenceRule.FREQ))
		{
			return new TThRecurrenceRule(interval,until);
		}
		else if (frequency.equalsIgnoreCase(MWFRecurrenceRule.FREQ))
		{
			return new MWFRecurrenceRule(interval,until);
		} 
		else if (frequency.equalsIgnoreCase(MonthlyRecurrenceRule.FREQ))
		{
			return new MonthlyRecurrenceRule(interval, until);
		}
		else if (frequency.equalsIgnoreCase(YearlyRecurrenceRule.FREQ))
		{
			return new YearlyRecurrenceRule(interval, until);
		}
		else if (frequency.equalsIgnoreCase(MWRecurrenceRule.FREQ))
		{
			return new MWRecurrenceRule(interval, until);
		}
		else if (frequency.equalsIgnoreCase(SMWRecurrenceRule.FREQ))
		{
			return new SMWRecurrenceRule(interval, until);
		}
		else if (frequency.equalsIgnoreCase(SMTWRecurrenceRule.FREQ))
		{
			return new SMTWRecurrenceRule(interval, until);
		}
		else if (frequency.equalsIgnoreCase(STTRecurrenceRule.FREQ))
		{
			return new STTRecurrenceRule(interval, until);
		}
		//add more here
     
		return null;
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * ResourceService implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	@Override
	public String getLabel()
	{
		return "calendar";
	}

	@Override
	public boolean willArchiveMerge()
	{
		return true;
	}

	@Override
	public HttpAccess getHttpAccess()
	{
		return new HttpAccess()
		{
			public void handleAccess(HttpServletRequest req, HttpServletResponse res, Reference ref,
					Collection copyrightAcceptedRefs) throws EntityPermissionException, EntityNotDefinedException,
					EntityAccessOverloadException, EntityCopyrightException
			{
				String calRef = calendarReference(ref.getContext(), SiteService.MAIN_CONTAINER);

				if (REF_TYPE_CALENDAR_PDF.equals(ref.getSubType()))
				{
					handleAccessPdf(req, res, ref, calRef);
				}
				else if (REF_TYPE_CALENDAR_ICAL.equals(ref.getSubType()))
 				{
					handleAccessIcal(req, res, ref, calRef);
 				}
				else if (REF_TYPE_CALENDAR_OPAQUEURL.equals(ref.getSubType()))
 				{
					handleAccessOpaqueUrl(req, res, ref, calRef);
 				}
				else // we only access the opaq, pdf & ical reference
				{
					throw new EntityNotDefinedException(ref.getReference());
				}
			}
		};
	}

	@Override
	public boolean parseEntityReference(String reference, Reference ref) {
		if (reference.startsWith(CalendarService.REFERENCE_ROOT)) {
			String[] parts = reference.split(Entity.SEPARATOR);

			String subType = null;
			String context = null;
			String id = null;
			String container = null;

			// the first part will be null, then next the service, the third will be "calendar" or "event"
			if (parts.length > 2) {
				subType = parts[2];
				// Opaque URLs put the opaque GUID where the context ID would normally be:
				if (REF_TYPE_CALENDAR_OPAQUEURL.equals(subType) && parts.length > 3) {
					parts[3] = mapOpaqueGuidToContextId(ref, parts[3]);
				}
				if ( REF_TYPE_CALENDAR.equals(subType) || 
					 REF_TYPE_CALENDAR_PDF.equals(subType) || 
					 REF_TYPE_CALENDAR_ICAL.equals(subType) ||
					 REF_TYPE_CALENDAR_SUBSCRIPTION.equals(subType) ||
					 REF_TYPE_CALENDAR_OPAQUEURL.equals(subType))
				{
					// next is the context id
					if (parts.length > 3) {
						context = parts[3];

						// next is the optional calendar id
						if (parts.length > 4) {
							id = parts[4];
						}
					}
				} else if (REF_TYPE_EVENT.equals(subType) || REF_TYPE_EVENT_SUBSCRIPTION.equals(subType) || REF_TYPE_DASHBOARD.equals(subType)) {
					// next three parts are context, channel (container) and event id
					if (parts.length > 5) {
						context = parts[3];
						container = parts[4];
						id = parts[5];
					}
				} else
					log.warn(".parseEntityReference(): unknown calendar subtype: " + subType + " in ref: " + reference);
			}

			// Translate context alias into site id if necessary
			if ((context != null) && (context.length() > 0)) {
				if (!siteService.siteExists(context)) {
					try {
						Calendar calendarObj = getCalendar(aliasService.getTarget(context));
						context = calendarObj.getContext();
					} catch (IdUnusedException ide) {
						log.info(".parseEntityReference():"+ide.toString()); 
						return false;
					} catch (PermissionException pe) {
						log.info(".parseEntityReference():"+pe.toString());
						return false;
					} catch (Exception e) {
						log.warn(".parseEntityReference(): ", e);
						return false;
					}
				}
			}
			
			// if context still isn't valid, then no valid alias or site was specified
			if (!siteService.siteExists(context)) {
				log.warn(".parseEntityReference() no valid site or alias: " + context);
				return false;
			}

			// build updated reference          
			ref.set(APPLICATION_ID, subType, id, container, context);

			return true;
		}

		return false;
	}

	@Override
	public String getEntityDescription(Reference ref)
	{
		// double check that it's mine
		if (!APPLICATION_ID.equals(ref.getType())) return null;
		

		String rv = "Calendar: " + ref.getReference();

		try
		{
			// if this is a calendar
			if (REF_TYPE_CALENDAR.equals(ref.getSubType()) || REF_TYPE_CALENDAR_PDF.equals(ref.getSubType()))
			{
				Calendar cal = getCalendar(ref.getReference());
				rv = "Calendar: " + cal.getId() + " (" + cal.getContext() + ")";
			}

			// otherwise an event
			else if (REF_TYPE_EVENT.equals(ref.getSubType()))
			{
				rv = "Event: " + ref.getReference();
			}
		}
		catch (PermissionException ignore) {}
		catch (IdUnusedException ignore) {}
		catch (NullPointerException ignore) {}

		return rv;
	}

	@Override
	public ResourceProperties getEntityResourceProperties(Reference ref)
	{
		// double check that it's mine
		if (!APPLICATION_ID.equals(ref.getType())) return null;

		ResourceProperties props = null;

		try
		{
			// if this is a calendar
			if (REF_TYPE_CALENDAR.equals(ref.getSubType()) || REF_TYPE_CALENDAR_PDF.equals(ref.getSubType()) || REF_TYPE_CALENDAR_SUBSCRIPTION.equals(ref.getSubType()))
			{
				Calendar cal = getCalendar(ref.getReference());
				props = cal.getProperties();
			}

			// otherwise an event
			else if (REF_TYPE_EVENT.equals(ref.getSubType()))
			{
				Calendar cal = getCalendar(calendarReference(ref.getContext(), ref.getContainer()));
				CalendarEvent event = cal.getEvent(ref.getId());
				props = event.getProperties();
			}
			else if (REF_TYPE_EVENT_SUBSCRIPTION.equals(ref.getSubType()))
			{
				Calendar cal = getCalendar(calendarSubscriptionReference(ref.getContext(), ref.getContainer()));
				CalendarEvent event = cal.getEvent(ref.getId());
				props = event.getProperties();
			}

			else
				log.warn(".getEntityResourceProperties(): unknown calendar ref subtype: " + ref.getSubType() + " in ref: "
						+ ref.getReference());
		}
		catch (PermissionException e)
		{
			log.warn(".getEntityResourceProperties(): " + e);
		}
		catch (IdUnusedException ignore)
		{
			// This just means that the resource once pointed to as an attachment or something has been deleted.
		}
		catch (NullPointerException e)
		{
			log.warn(".getEntityResourceProperties(): " + e);
		}

		return props;
	}

	@Override
	public Entity getEntity(Reference ref)
	{
		// double check that it's mine
		if (!APPLICATION_ID.equals(ref.getType())) return null;

		Entity rv = null;

		try
		{
			// if this is a calendar
			if (REF_TYPE_CALENDAR.equals(ref.getSubType()) || REF_TYPE_CALENDAR_PDF.equals(ref.getSubType()) || REF_TYPE_CALENDAR_SUBSCRIPTION.equals(ref.getSubType()))
			{
				rv = getCalendar(ref.getReference());
			}

			// otherwise a event
			else if (REF_TYPE_EVENT.equals(ref.getSubType()))
			{
				Calendar cal = getCalendar(calendarReference(ref.getContext(), ref.getContainer()));
				rv = cal.getEvent(ref.getId());
			}
			else if (REF_TYPE_EVENT_SUBSCRIPTION.equals(ref.getSubType()))
			{
				Calendar cal = getCalendar(calendarSubscriptionReference(ref.getContext(), ref.getContainer()));
				rv = cal.getEvent(ref.getId());
			}

			else
				log.warn("getEntity(): unknown calendar ref subtype: " + ref.getSubType() + " in ref: " + ref.getReference());
		}
		catch (PermissionException e)
		{
			log.warn("getEntity(): " + e);
		}
		catch (IdUnusedException e)
		{
			log.warn("getEntity(): " + e);
		}
		catch (NullPointerException e)
		{
			log.warn(".getEntity(): " + e);
		}

		return rv;
	}

	@Override
	public Collection getEntityAuthzGroups(Reference ref, String userId)
	{
		// double check that it's mine
		if (!APPLICATION_ID.equals(ref.getType())) return null;

		Collection rv = new Vector();

		// for events:
		// if access set to SITE (or PUBLIC), use the event, calendar and site authzGroups.
		// if access set to GROUPED, use the event, and the groups, but not the calendar or site authzGroups.
		// if the user has SECURE_ALL_GROUPS in the context, ignore GROUPED access and treat as if SITE

		// for Calendars: use the calendar and site authzGroups.

		try
		{
			// for event
			if (REF_TYPE_EVENT.equals(ref.getSubType()))
			{
				// event
				rv.add(ref.getReference());
				
				boolean grouped = false;
				Collection groups = null;

				// check SECURE_ALL_GROUPS - if not, check if the event has groups or not
				// TODO: the last param needs to be a ContextService.getRef(ref.getContext())... or a ref.getContextAuthzGroup() -ggolden
				if ((userId == null) || ((!securityService.isSuperUser(userId)) && (!securityService.unlock(userId, SECURE_ALL_GROUPS, siteService.siteReference(ref.getContext())))))
				{
					// get the calendar to get the message to get group information
					String calendarRef = calendarReference(ref.getContext(), ref.getContainer());
					Calendar c = findCalendar(calendarRef);
					if (c != null)
					{
						CalendarEvent e = ((BaseCalendarEdit) c).findEvent(ref.getId());
						if (e != null)
						{
							grouped = EventAccess.GROUPED == e.getAccess();
							groups = e.getGroups();
						}
					}
				}

				if (grouped)
				{
					// groups
					rv.addAll(groups);
				}

				// not grouped
				else
				{
					// calendar
					rv.add(calendarReference(ref.getContext(), ref.getContainer()));

					// site
					ref.addSiteContextAuthzGroup(rv);
				}
			}

			// for calendar
			else
			{
				// calendar
				rv.add(calendarReference(ref.getContext(), ref.getId()));

				// site
				ref.addSiteContextAuthzGroup(rv);
			}
		}
		catch (Throwable e)
		{
			log.warn("getEntityAuthzGroups(): " + e);
		}

		return rv;
	}

	@Override
	public String getEntityUrl(Reference ref) {
		// double check that it's mine
		if (!APPLICATION_ID.equals(ref.getType())) return null;

		String rv = null;

		try {
			// if this is a calendar
			if (REF_TYPE_CALENDAR.equals(ref.getSubType()) || REF_TYPE_CALENDAR_PDF.equals(ref.getSubType())) {
				Calendar cal = getCalendar(ref.getReference());
				rv = cal.getUrl();
			}
			// otherwise a event
			else if (REF_TYPE_EVENT.equals(ref.getSubType())) {
				Calendar cal = getCalendar(calendarReference(ref.getContext(), ref.getContainer()));
				CalendarEvent event = cal.getEvent(ref.getId());
				rv = event.getUrl();
			}
			// an event referenced from the dashboard
			else if (REF_TYPE_DASHBOARD.equals(ref.getSubType())) {
				String baseUrl = getDirectToolUrl(ref.getContext());
				String eventReference = ref.getReference().replaceFirst(REF_TYPE_DASHBOARD, REF_TYPE_EVENT);
				rv = String.format(EVENT_URL_PATTERN, baseUrl, eventReference);
			} else {
				log.warn("getEntityUrl(): unknown calendar ref subtype: " + ref.getSubType() + " in ref: " + ref.getReference());	
			}
			
		} catch (PermissionException | IdUnusedException | NullPointerException e) {
			log.warn(".getEntityUrl(): " + e);
		}

		return rv;
	}

	@Override
	public String archive(String siteId, Document doc, Stack stack, String archivePath, List attachments)
	{
		// prepare the buffer for the results log
		StringBuilder results = new StringBuilder();

		// start with an element with our very own (service) name
		Element element = doc.createElement(CalendarService.class.getName());
		((Element) stack.peek()).appendChild(element);
		stack.push(element);

		// get the channel associated with this site
		String calRef = calendarReference(siteId, SiteService.MAIN_CONTAINER);

		results.append("archiving calendar " + calRef + ".\n");

		try
		{
			// do the channel
			Calendar cal = getCalendar(calRef);
			Element containerElement = cal.toXml(doc, stack);
			stack.push(containerElement);

			// do the messages in the channel
			Iterator events = cal.getEvents(null, null).iterator();
			while (events.hasNext())
			{
				CalendarEvent event = (CalendarEvent) events.next();
				event.toXml(doc, stack);

				// collect message attachments
				List atts = event.getAttachments();
				for (int i = 0; i < atts.size(); i++)
				{
					Reference ref = (Reference) atts.get(i);
					// if it's in the attachment area, and not already in the list
					if ((ref.getReference().startsWith("/content/attachment/")) && (!attachments.contains(ref)))
					{
						attachments.add(ref);
					}
				}
			}

			stack.pop();
		}
		catch (Exception any)
		{
			log.warn(".archve: exception archiving messages for service: " + CalendarService.class.getName() + " channel: "
					+ calRef);
		}

		stack.pop();

		return results.toString();
	}

	@Override
	public String merge(String siteId, Element root, String archivePath, String fromSiteId, MergeConfig mcx)
	{

		// prepare the buffer for the results log
		StringBuilder results = new StringBuilder();

		// get the channel associated with this site
		String calendarRef = calendarReference(siteId, SiteService.MAIN_CONTAINER);

		int count = 0;

		try
		{
			Calendar calendar = null;
			try
			{
				calendar = getCalendar(calendarRef);
			}
			catch (IdUnusedException e)
			{
				CalendarEdit edit = addCalendar(calendarRef);
				commitCalendar(edit);
				calendar = edit;
			}

			// Load up all the calendar titles from existing entries
			Set<String> calendarTitles = calendar.getEvents(null, null).stream()
				.map(CalendarEvent::getDisplayName)
				.collect(Collectors.toCollection(LinkedHashSet::new));
			log.debug("calendarTitles: {}", calendarTitles);

			// pass the DOM to get new event ids, and adjust attachments
			NodeList children2 = root.getChildNodes();
			int length2 = children2.getLength();
			for (int i2 = 0; i2 < length2; i2++)
			{
				Node child2 = children2.item(i2);
				if (child2.getNodeType() == Node.ELEMENT_NODE)
				{
					Element element2 = (Element) child2;

					// get the "calendar" child
					if (element2.getTagName().equals("calendar"))
					{
						NodeList children3 = element2.getChildNodes();
						final int length3 = children3.getLength();
						for (int i3 = 0; i3 < length3; i3++)
						{
							Node child3 = children3.item(i3);
							if (child3.getNodeType() == Node.ELEMENT_NODE)
							{
								Element element3 = (Element) child3;

								if (element3.getTagName().equals("properties"))
								{
									NodeList children8 = element3.getChildNodes();
									final int length8 = children8.getLength();
									for (int i8 = 0; i8 < length8; i8++)
									{
										Node child8 = children8.item(i8);
										if (child8.getNodeType() == Node.ELEMENT_NODE)
										{
											Element element8 = (Element) child8;

											// for "event" children
											if (element8.getTagName().equals("property"))
											{
												String pName = element8.getAttribute("name");
												if ((pName != null) && (pName.equalsIgnoreCase("CHEF:calendar-fields")))
												{
													String pValue = element8.getAttribute("value");
													if ("BASE64".equalsIgnoreCase(element8.getAttribute("enc")))
													{
														pValue = Xml.decodeAttribute(element8, "value");
													}

													if (pValue != null)
													{
														try
														{
															CalendarEdit calEdit = editCalendar(calendarRef);
															String calFields = StringUtils.trimToEmpty(calEdit.getEventFields());

															if (calFields != null)
																pValue = calFields + ADDFIELDS_DELIMITER + pValue;

															calEdit.setEventFields(pValue);
															commitCalendar(calEdit);
														}
														catch (Exception e)
														{
															log.warn(".merge() when editing calendar: exception: ", e);
														}
													}
												}
											}
										}
									}
								}

								// for "event" children
								if (element3.getTagName().equals("event"))
								{
									// adjust the id
									String oldId = element3.getAttribute("id");
									String newId = getUniqueId();
									element3.setAttribute("id", newId);

									if ( ! shouldMergeEvent(element3) ) continue;

									// get the attachment kids
									NodeList children5 = element3.getChildNodes();
									final int length5 = children5.getLength();
									for (int i5 = 0; i5 < length5; i5++)
									{
										Node child5 = children5.item(i5);
										if (child5.getNodeType() == Node.ELEMENT_NODE)
										{
											Element element5 = (Element) child5;

											// for "attachment" children
											if (element5.getTagName().equals("attachment"))
											{
												// map the attachment area folder name
												String oldUrl = element5.getAttribute("relative-url");
												String toolTitle = toolManager.getTool("sakai.schedule").getTitle();
												ContentResource attachment = contentHostingService.copyAttachment(oldUrl, siteId, toolTitle, mcx);
												if ( attachment != null ) {
													String newUrl = attachment.getReference();
													element5.setAttribute("relative-url", Validator.escapeQuestionMark(newUrl));
												}
											}
										}
									}

									// create a new message in the calendar
									CalendarEventEdit edit = calendar.mergeEvent(element3);
									String title = edit.getDisplayName();
									if ( StringUtils.isNotBlank(title) && calendarTitles.contains(title) ) {
										results.append("merging calendar " + calendarRef + "skipping duplicate event: "+title+"\n");
										log.debug("merge: skipping duplicate calendar event: {}", title);
										continue;
									}
									String description = edit.getDescriptionFormatted();
									description = ltiService.fixLtiLaunchUrls(description, siteId, mcx);
									description = linkMigrationHelper.migrateLinksInMergedRTE(siteId, mcx, description);
									edit.setDescriptionFormatted(description);

									calendar.commitEvent(edit);
									count++;
								}
							}
						}
					}
				}
			}
		}
		catch (Exception any)
		{
			log.warn(".merge(): exception: ", any);
		}

		results.append("merging calendar " + calendarRef + " (" + count + ") messages.\n");
		return results.toString();
	}

	/*
	 * Look at the properties of the event and determine if it should be merged or ignored
	 */
	private boolean shouldMergeEvent(Element el) {
		NodeList nodeList = el.getElementsByTagName("property");

		for (int i = 0; i < nodeList.getLength(); i++) {
			Element prop = (Element) nodeList.item(i);
			String name = prop.getAttribute("name");
			String value = prop.getAttribute("value");
			if ("BASE64".equalsIgnoreCase(prop.getAttribute("enc"))) {
				value = Xml.decodeAttribute(prop, "value");
			}

			boolean shouldMerge = shouldMergeProperty(name, value);
			if (!shouldMerge) {
				return false;
			}
		}
		return true;
	}

	private boolean shouldMergeProperty(String name, String value) {
		if ( StringUtils.contains(name, CalendarConstants.EVENT_OWNED_BY_TOOL_ID) &&
			(StringUtils.contains(value, AssignmentServiceConstants.ASSIGNMENT_TOOL_ID) ||
			StringUtils.contains(value, DiscussionForumService.FORUMS_TOOL_ID ) ||
			StringUtils.contains(value, SamigoConstants.TOOL_ID) ) ) {
			log.debug("Not importing assignment event from tool {}", value);
			return false;
		}

		// Do not import events associated with an assignment - backwards compatibility
		if ( StringUtils.contains(name, CalendarConstants.NEW_ASSIGNMENT_DUEDATE_CALENDAR_ASSIGNMENT_ID) ||
				StringUtils.contains(name, CalendarConstants.NEW_ASSIGNMENT_OPEN_DATE_ANNOUNCED) ) {
			log.debug("Not importing assignment event {}", value);
			return false;
		}

		// Samigo does not mark its events, but the notification message is consitent - backwards compatibility
		if ( StringUtils.equals(name, "CHEF:description") && StringUtils.contains(value, "samigo-app/servlet/Login")) {
			log.debug("Not importing samigo event based on description containing Samigo launch URL");
			return false;
		}

		// Discussion topic deadlines include calendar-url values inevitably pointing to the wrong place - backwards compatibility
		if ( StringUtils.equals(name, "CHEF:calendar-url") && StringUtils.contains(value, "portal/site")) {
			log.debug("Not importing discussion topic deadline event based on calendar-url {}", value);
			return false;
		}
		return true;
	}

	public Map<String, String> transferCopyEntities(String fromContext, String toContext, List<String> resourceIds, List<String> options) {

		Map<String, String> transversalMap = new HashMap<String, String>();

		// get the channel associated with this site
		String oCalendarRef = calendarReference(fromContext, SiteService.MAIN_CONTAINER);

		Calendar oCalendar = null;
		try
		{
			oCalendar = getCalendar(oCalendarRef);

			// new calendar
			CalendarEdit nCalendar = null;
			String nCalendarRef = calendarReference(toContext, SiteService.MAIN_CONTAINER);
			try
			{
				nCalendar = editCalendar(nCalendarRef);
			}
			catch (IdUnusedException e)
			{
				try
				{
					nCalendar = addCalendar(nCalendarRef);
				}
				catch (IdUsedException ignore) {}
				catch (IdInvalidException ignore) {}
			}
			catch (PermissionException ignore) {}
			catch (InUseException ignore) {}

			if (nCalendar != null)
			{
				List oEvents = oCalendar.getEvents(null, null);

				String oFields = StringUtils.trimToNull(oCalendar.getEventFields());
				String nFields = StringUtils.trimToNull(nCalendar.getEventFields());
				String allFields = "";

				if (oFields != null)
				{
					if (nFields != null)
					{
						allFields = nFields + ADDFIELDS_DELIMITER + oFields;
					}
					else
					{
						allFields = oFields;
					}
					nCalendar.setEventFields(allFields);
				}

				for (int i = 0; i < oEvents.size(); i++)
				{
					CalendarEventEdit oEvent = (CalendarEventEdit) oEvents.get(i);
					try
					{
						log.debug("Found Event: {}", oEvent.getDisplayName());
						ResourceProperties props = oEvent.getProperties();
						Iterator<String> propNames = props.getPropertyNames();
						boolean shouldMerge = true;
						while (propNames.hasNext()) {
							String key = propNames.next();
							String value = props.getProperty(key);
							if ( ! shouldMergeProperty(key, value) ) {
								shouldMerge = false;
								break;
							}
						}
						if ( ! shouldMerge ) {
							log.debug("Not merging event: {}", oEvent.getDisplayName());
							continue;
						}

						String description = oEvent.getDescriptionFormatted();
						description = ltiService.fixLtiLaunchUrls(description, fromContext, toContext, transversalMap);
						oEvent.setDescriptionFormatted(description);

						CalendarEvent e = nCalendar.addEvent(oEvent.getRange(), oEvent.getDisplayName(), oEvent.getDescriptionFormatted(),
								oEvent.getType(), oEvent.getLocation(), oEvent.getAttachments());

						try
						{
							BaseCalendarEventEdit eEdit = (BaseCalendarEventEdit) nCalendar.getEditEvent(e.getId(),EVENT_ADD_CALENDAR );
							// properties
							ResourcePropertiesEdit p = eEdit.getPropertiesEdit();
							p.clear();
							p.addAll(oEvent.getProperties());

							// attachment
							List oAttachments = eEdit.getAttachments();
							List nAttachments = entityManager.newReferenceList();
							for (int n = 0; n < oAttachments.size(); n++)
							{
								Reference oAttachmentRef = (Reference) oAttachments.get(n);
								String oAttachmentId = ((Reference) oAttachments.get(n)).getId();
								if (oAttachmentId.indexOf(fromContext) != -1)
								{
									// replace old site id with new site id in attachments
									String nAttachmentId = oAttachmentId.replaceAll(fromContext, toContext);
									try
									{
										ContentResource attachment = contentHostingService.getResource(nAttachmentId);
										nAttachments.add(entityManager.newReference(attachment.getReference()));
									}
									catch (IdUnusedException ee)
									{
										try
										{
											ContentResource oAttachment = contentHostingService.getResource(oAttachmentId);
											try
											{
												if (contentHostingService.isAttachmentResource(nAttachmentId))
												{
													// add the new resource into attachment collection area
													ContentResource attachment = contentHostingService.addAttachmentResource(
															Validator.escapeResourceName(oAttachment.getProperties().getProperty(ResourceProperties.PROP_DISPLAY_NAME)), 
															toContext, 
															toolManager.getTool("sakai.schedule").getTitle(),
															oAttachment.getContentType(),
															oAttachment.getContent(), 
															oAttachment.getProperties());
													// add to attachment list
													nAttachments.add(entityManager.newReference(attachment.getReference()));
												}
												else
												{
													// add the new resource into resource area
													ContentResource attachment = contentHostingService.addResource(
															Validator.escapeResourceName(oAttachment.getProperties().getProperty(ResourceProperties.PROP_DISPLAY_NAME)),
															toContext, 
															1, 
															oAttachment.getContentType(), 
															oAttachment.getContent(), 
															oAttachment.getProperties(), 
															NotificationService.NOTI_NONE);
													// add to attachment list
													nAttachments.add(entityManager.newReference(attachment.getReference()));
												}
											}
											catch (Exception eeAny)
											{
												// if the new resource cannot be added
												log.warn(" cannot add new attachment with id=" + nAttachmentId);
											}
										}
										catch (Exception eAny)
										{
											// if cannot find the original attachment, do nothing.
											log.warn(" cannot find the original attachment with id=" + oAttachmentId);
										}
									}
									catch (Exception any)
									{
										log.warn(this + any.getMessage());
									}

								}
								else
								{
									nAttachments.add(oAttachmentRef);
								}
							}
							eEdit.replaceAttachments(nAttachments);
							
							// recurrence rules
							RecurrenceRule rule = oEvent.getRecurrenceRule();
							eEdit.setRecurrenceRule(rule);

							RecurrenceRule exRule = oEvent.getExclusionRule();
							eEdit.setExclusionRule(exRule);

							// commit new event
							m_storage.commitEvent(nCalendar, eEdit);
							transversalMap.put(oEvent.getId(), eEdit.getId());
						}
						catch (InUseException ignore) {}
					}
					catch (PermissionException ignore) {}
				}
				// commit new calendar
				m_storage.commitCalendar(nCalendar);
				((BaseCalendarEdit) nCalendar).closeEdit();
			} // if
		}
		catch (IdUnusedException ignore) {}
		catch (PermissionException ignore) {}

		return transversalMap;
	} // importResources

	@Override
	public void updateEntityReferences(String toContext, Map<String, String> transversalMap){
		if(transversalMap != null && transversalMap.size() > 0){
			Set<Entry<String, String>> entrySet = (Set<Entry<String, String>>) transversalMap.entrySet();

			try
			{
				String toSiteId = toContext;	
				String calendarId = calendarReference(toSiteId, SiteService.MAIN_CONTAINER);
				Calendar calendarObj = getCalendar(calendarId);	
				List calEvents = calendarObj.getEvents(null,null);

				for (int i = 0; i < calEvents.size(); i++)
				{
					try
					{	
						CalendarEvent ce = (CalendarEvent) calEvents.get(i);	
						String msgBodyFormatted = ce.getDescriptionFormatted();						
						boolean updated = false;
						StringBuffer msgBodyPreMigrate = new StringBuffer(msgBodyFormatted);
						msgBodyFormatted = linkMigrationHelper.migrateAllLinks(entrySet, msgBodyFormatted);
						if(!msgBodyFormatted.equals(msgBodyPreMigrate.toString())){
						
							CalendarEventEdit edit = calendarObj.getEditEvent(ce.getId(), org.sakaiproject.calendar.api.CalendarService.EVENT_MODIFY_CALENDAR);
							edit.setDescriptionFormatted(msgBodyFormatted);
							calendarObj.commitEvent(edit);
						}
					}
					catch (IdUnusedException e)
					{
						log.debug(".IdUnusedException " + e);
					}
					catch (PermissionException e)
					{
						log.debug(".PermissionException " + e);
					}
					catch (InUseException e)
					{
						log.debug(".InUseException delete" + e);
					}
				}
			}
			catch (Exception e)
			{
				log.info("importSiteClean: End removing Calendar data" + e);
			}
		}		  		  
	}
	
	@Override
	public String[] myToolIds()
	{
		String[] toolIds = { "sakai.schedule" };
		return toolIds;
	}

	@Override
	public void contextCreated(String context, boolean toolPlacement)
	{
		if (toolPlacement) enableSchedule(context);
	}

	@Override
	public void contextUpdated(String context, boolean toolPlacement)
	{
		if (toolPlacement) enableSchedule(context);
	}

	@Override
	public void contextDeleted(String context, boolean toolPlacement)
	{
		disableSchedule(context);
	}

	/**
	 * Setup a calendar for the site.
	 * 
	 * @param context
	 *        The site ID.
	 */
	protected void enableSchedule(String context)
	{
		// form the calendar name
		String calRef = calendarReference(context, SiteService.MAIN_CONTAINER);

		// see if there's a calendar
		try
		{
			getCalendar(calRef);
		}
		catch (IdUnusedException un)
		{
			try
			{
				// create a calendar
				CalendarEdit edit = addCalendar(calRef);
				commitCalendar(edit);
			}
			catch (IdUsedException ignore) {}
			catch (IdInvalidException ignore) {}
		}
		catch (PermissionException ignore) {}
	}

	/**
	 * Remove a calendar for the site.
	 * 
	 * @param context
	 *        The site ID.
	 */
	protected void disableSchedule(String context)
	{
		// TODO: currently we do not remove a calendar when the tool is removed from the site or the site is deleted -ggolden
	}

	@Override
	public void update(Observable o, Object arg) {
		if (arg instanceof Event) {
			Event event = (Event) arg;
			if (EVENT_MODIFY_CALENDAR.equals(event.getEvent())) {
				cache.remove(event.getResource());
			}
		}
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Calendar implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	public class BaseCalendarEdit extends Observable implements CalendarEdit, SessionBindingListener
	{
		/** The context in which this calendar exists. */
		protected String m_context = null;

		/** Store the unique-in-context calendar id. */
		protected String m_id = null;

		/** The properties. */
		protected ResourcePropertiesEdit m_properties = null;

		/** When true, the calendar has been removed. */
		protected boolean m_isRemoved = false;

		/** The event code for this edit. */
		protected String m_event = null;

		/** Active flag. */
		protected boolean m_active = false;

		/**
		 * Construct with an id.
		 * 
		 * @param ref
		 *        The calendar reference.
		 */
		public BaseCalendarEdit(String ref)
		{
			// set the ids
			Reference r = entityManager.newReference(ref);
			m_context = r.getContext();
			m_id = r.getId();

			// setup for properties
			m_properties = new BaseResourcePropertiesEdit();

		} // BaseCalendarEdit

		/**
		 * Construct as a copy of another.
		 * 
		 * @param other
		 *        The other to copy.
		 */
		public BaseCalendarEdit(Calendar other)
		{
			// set the ids
			m_context = other.getContext();
			m_id = other.getId();

			// setup for properties
			m_properties = new BaseResourcePropertiesEdit();
			m_properties.addAll(other.getProperties());

		} // BaseCalendarEdit
		
		protected BaseCalendarEdit() {
			m_properties = new BaseResourcePropertiesEdit();			
		}

		/**
		 * Construct from a calendar (and possibly events) already defined in XML in a DOM tree. The Calendar is added to storage.
		 * 
		 * @param el
		 *        The XML DOM element defining the calendar.
		 */
		public BaseCalendarEdit(Element el)
		{
			// setup for properties
			m_properties = new BaseResourcePropertiesEdit();

			m_id = el.getAttribute("id");
			m_context = el.getAttribute("context");

			// the children (properties, ignore events)
			NodeList children = el.getChildNodes();
			final int length = children.getLength();
			for (int i = 0; i < length; i++)
			{
				Node child = children.item(i);
				if (child.getNodeType() != Node.ELEMENT_NODE) continue;
				Element element = (Element) child;

				// look for properties (ignore possible "event" entries)
				if (element.getTagName().equals("properties"))
				{
					// re-create properties
					m_properties = new BaseResourcePropertiesEdit(element);
				}
			}

		} // BaseCalendarEdit

		/**
		 * Set the calendar as removed.
		 * 
		 * @param event
		 *        The tracking event associated with this action.
		 */
		public void setRemoved(Event event)
		{
			m_isRemoved = true;

			// notify observers
			notify(event);

			// now clear observers
			deleteObservers();

		} // setRemoved

		/**
		 * Access the context of the resource.
		 * 
		 * @return The context.
		 */
		public String getContext()
		{
			return m_context;

		} // getContext

		/**
		 * Access the id of the resource.
		 * 
		 * @return The id.
		 */
		public String getId()
		{
			return m_id;

		} // getId

		/**
		 * Access the URL which can be used to access the resource.
		 * 
		 * @return The URL which can be used to access the resource.
		 */
		public String getUrl()
		{
			return getAccessPoint(false) + SEPARATOR + getId() + SEPARATOR; // %%% needs fixing re: context

		} // getUrl

		/**
		 * Access the internal reference which can be used to access the resource from within the system.
		 * 
		 * @return The the internal reference which can be used to access the resource from within the system.
		 */
		public String getReference()
		{
			return calendarReference(m_context, m_id);

		} // getReference

		@Override
		public String getReference(String rootProperty)
		{
			return getReference();
		}

		@Override
		public String getUrl(String rootProperty)
		{
			return getUrl();
		}

		/**
		 * Access the collection's properties.
		 * 
		 * @return The collection's properties.
		 */
		public ResourceProperties getProperties()
		{
			return m_properties;

		} // getProperties

		/**
		 ** check if this calendar allows ical exports
		 ** @return true if the calender allows exports; false if not
		 **/
		public boolean getExportEnabled()
		{
			String enable = m_properties.getProperty(CalendarService.PROP_ICAL_ENABLE);
			return Boolean.valueOf(enable);
		}

		/**
		 ** set if this calendar allows ical exports
		 ** @return true if the calender allows exports; false if not
		 **/
		public void setExportEnabled( boolean enable )
		{
			m_properties.addProperty(CalendarService.PROP_ICAL_ENABLE, String.valueOf(enable));
		}

		/**
		 ** Get the time of the last modify to this calendar
		 ** @return String representation of current time (may be null if not initialized)
		 **/
		public Time getModified()
		{
			String timeStr = m_properties.getProperty(ResourceProperties.PROP_MODIFIED_DATE);
			if ( timeStr == null )
				return null;
			else
				return timeService.newTimeGmt(timeStr);
		}

		/**
		 ** Set the time of the last modify for this calendar to now
		 ** @return true if successful; false if not
		 **/
		public void setModified()
		{
			String currentUser = sessionManager.getCurrentSessionUserId();
			String now = timeService.newTime().toString();
			m_properties.addProperty(ResourceProperties.PROP_MODIFIED_BY, currentUser);
			m_properties.addProperty(ResourceProperties.PROP_MODIFIED_DATE, now);
		}
	
		/**
		 * check permissions for getEvents() and getEvent().
		 * 
		 * @return true if the user is allowed to get events from the calendar, false if not.
		 */
		public boolean allowGetEvents()
		{
			return unlockCheck(AUTH_READ_CALENDAR, getReference());

		} // allowGetEvents

		@Override
		public boolean allowGetEvent(String eventId)
		{
			return unlockCheck(AUTH_READ_CALENDAR, eventReference(m_context, m_id, eventId));
		}

		/**
		 * Return a List of all or filtered events in the calendar. The order in which the events will be found in the iteration is by event start date.
		 * 
		 * @param range
		 *        A time range to limit the iterated events. May be null; all events will be returned.
		 * @param filter
		 *        A filtering object to accept events into the iterator, or null if no filtering is desired.
		 * @return a List of all or filtered CalendarEvents in the calendar (may be empty).
		 * @exception PermissionException
		 *            if the user does not have read permission to the calendar.
		 */
		public List getEvents(TimeRange range, Filter filter) throws PermissionException {
			return getEvents(range, filter, null);
		}

		public List getEvents(TimeRange range, Filter filter, Integer limit) throws PermissionException {

			// check security (throws if not permitted)
			unlock(AUTH_READ_CALENDAR, getReference());

			List events = m_storage.getEvents(this, range, limit);

			// now filter out the events to just those in the range
			// Note: if no range, we won't filter, which means we don't expand recurring events, but just
			// return it as a single event. This is very good for an archive... -ggolden
			if (range != null)
			{
				events = filterEvents(events, range);
			}

			if (events.size() == 0) return events;

			// filter out based on the filter
			if (filter != null)
			{
				List filtered = new Vector();
				for (int i = 0; i < events.size(); i++)
				{
					Event event = (Event) events.get(i);
					if (filter.accept(event)) filtered.add(event);
				}
				if (filtered.size() == 0) return filtered;
				events = filtered;
			}

			// remove any events that are grouped, and that the current user does not have permission to see
			Collection groupsAllowed = getGroupsAllowGetEvent();
			List allowedEvents = new Vector();
			for (Iterator i = events.iterator(); i.hasNext();)
			{
				CalendarEvent event = (CalendarEvent) i.next();
				if (event.getAccess() == EventAccess.SITE)
				{
					allowedEvents.add(event);
				}
				
				else
				{
					// if the user's Groups overlap the event's group refs it's grouped to, keep it
					if (EntityCollections.isIntersectionEntityRefsToEntities(event.getGroups(), groupsAllowed))
					{
						allowedEvents.add(event);
					}
				}
			}

			// sort - natural order is date ascending
			Collections.sort(allowedEvents);

			return allowedEvents;
		} // getEvents

		/**
		 * Filter the events to only those in the time range.
		 * 
		 * @param events
		 *        The full list of events.
		 * @param range
		 *        The time range.
		 * @return A list of events from the incoming list that overlap the given time range.
		 */
		protected List filterEvents(List events, TimeRange range)
		{
			List filtered = new Vector();
			for (int i = 0; i < events.size(); i++)
			{
				CalendarEvent event = (CalendarEvent) events.get(i);

				// resolve the event to the list of events in this range
				List resolved = ((BaseCalendarEventEdit) event).resolve(range);
				filtered.addAll(resolved);
			}

			return filtered;

		} // filterEvents

		/**
		 * Return a specific calendar event, as specified by event id.
		 * 
		 * @param eventId
		 *        The id of the event to get.
		 * @return the CalendarEvent that has the specified id.
		 * @exception IdUnusedException
		 *            If this id is not a defined event in this calendar.
		 * @exception PermissionException
		 *            If the user does not have any permissions to read the calendar.
		 */
		public CalendarEvent getEvent(String eventId) throws IdUnusedException, PermissionException
		{
			// check security on the event (throws if not permitted)
			unlock(AUTH_READ_CALENDAR, eventReference(m_context, m_id, eventId));

			CalendarEvent e = findEvent(eventId);

			if (e == null) throw new IdUnusedException(eventId);

			return e;

		} // getEvent

		/**
		 * check permissions for addEvent().
		 * 
		 * @return true if the user is allowed to addEvent(...), false if not.
		 */
		public boolean allowAddEvent()
		{
			// checking allow at the channel (site) level
			if (allowAddCalendarEvent()) return true;

			// if not, see if the user has any groups to which adds are allowed
			return (!getGroupsAllowAddEvent().isEmpty());

		} // allowAddEvent

		@Override
		public boolean allowAddCalendarEvent()
		{
			// check for events that will be calendar (site) -wide:
			// base the check for SECURE_ADD on the site and the calendar only (not the groups).

			// check security on the calendar (throws if not permitted)
			return unlockCheck(AUTH_ADD_CALENDAR, getReference());
		}

		/**
		 * Add a new event to this calendar.
		 * 
		 * @param range
		 *        The event's time range.
		 * @param displayName
		 *        The event's display name (PROP_DISPLAY_NAME) property value.
		 * @param description
		 *        The event's description as plain text (PROP_DESCRIPTION) property value.
		 * @param type
		 *        The event's calendar event type (PROP_CALENDAR_TYPE) property value.
		 * @param location
		 *        The event's calendar event location (PROP_CALENDAR_LOCATION) property value.
		 * @param attachments
		 *        The event attachments, a vector of Reference objects.
		 * @return The newly added event.
		 * @exception PermissionException
		 *            If the user does not have permission to modify the calendar.
		 */
		public CalendarEvent addEvent(TimeRange range, String displayName, String description, String type, String location, EventAccess access, Collection groups,
				List attachments) throws PermissionException
		{
			// securtiy check (any sort (group, site) of add)
			if (!allowAddEvent())
			{
				throw new PermissionException(sessionManager.getCurrentSessionUserId(), eventId(SECURE_ADD), getReference());
			}

			// make one
			// allocate a new unique event id
			String id = getUniqueId();

			// get a new event in the info store
			CalendarEventEdit edit = m_storage.putEvent(this, id);

			((BaseCalendarEventEdit) edit).setEvent(EVENT_ADD_CALENDAR);

			// set it up
			edit.setRange(range);
			edit.setDisplayName(displayName);
			edit.setDescription(description);
			edit.setType(type);
			edit.setLocation(location);
			if (toolManager.getCurrentPlacement() != null && toolManager.getCurrentPlacement().getContext() != null) {
				edit.setSiteId(toolManager.getCurrentPlacement().getContext());
			}
			edit.setCreator();
			
			// for site...
			if (access == EventAccess.SITE)
			{
				// if not allowd to SITE, will throw permission exception
				try
				{
					edit.clearGroupAccess();
				}
				catch (PermissionException e)
				{
					cancelEvent(edit);
					throw new PermissionException(sessionManager.getCurrentSessionUserId(), eventId(SECURE_ADD), getReference());
				}
			}
			
			// for grouped...
			else
			{
				Collection<Group> allowedGroups = this.getGroupsAllowAddEvent();
				Collection<Group> allowedSelected = filterAllowedAndSelectedGroups(groups, allowedGroups);
				Collection<Group> notAllowedSelected = filterNotAllowedAndSelectedGroups(groups, allowedGroups);
				// if not allowed to GROUP, will throw permission exception
				if(!allowedSelected.isEmpty()) {
					edit.setGroupAccess(allowedSelected, true);
					if(!notAllowedSelected.isEmpty()) {
						log.warn("Attempting to post to groups you don't belong to {} user={} lock={} resource={}", notAllowedSelected.toString(), sessionManager.getCurrentSessionUserId(), eventId(SECURE_ADD), getReference());
					}
				} else {
					try
					{
						edit.setGroupAccess(groups,true);
					}
					catch (PermissionException e)
					{
						cancelEvent(edit);
						throw new PermissionException(sessionManager.getCurrentSessionUserId(), eventId(SECURE_ADD), getReference());
					}
				}
			}

			edit.replaceAttachments(attachments);

			// commit it
			commitEvent(edit);

			return edit;

		} // addEvent

		/**
		 * @return only the groups allowed and selected
		 */
		private Collection<Group> filterAllowedAndSelectedGroups (Collection<Group> group, Collection<Group> groupAllowed) {
			return groupAllowed.stream().filter(it -> (group.contains(it))).collect(Collectors.toList());
		}

		/**
		 * @return groups not allowed and selected
		 */
		private Collection<Group> filterNotAllowedAndSelectedGroups (Collection<Group> group, Collection<Group> groupAllowed) {
			return group.stream().filter(it -> !(groupAllowed.contains(it))).collect(Collectors.toList());
		}
		
		/**
		 * Add a new event to this calendar.
		 * 
		 * @param range
		 *        The event's time range.
		 * @param displayName
		 *        The event's display name (PROP_DISPLAY_NAME) property value.
		 * @param description
		 *        The event's description as plain text (PROP_DESCRIPTION) property value.
		 * @param type
		 *        The event's calendar event type (PROP_CALENDAR_TYPE) property value.
		 * @param location
		 *        The event's calendar event location (PROP_CALENDAR_LOCATION) property value.
		 * @param attachments
		 *        The event attachments, a vector of Reference objects.
		 * @return The newly added event.
		 * @exception PermissionException
		 *            If the user does not have permission to modify the calendar.
		 */
		public CalendarEvent addEvent(TimeRange range, String displayName, String description, String type, String location, 
				List attachments) throws PermissionException
		{
			// make one
			CalendarEventEdit edit = addEvent();

			// set it up
			edit.setRange(range);
			edit.setDisplayName(displayName);
			edit.setDescription(description);
			edit.setType(type);
			edit.setLocation(location);
			edit.replaceAttachments(attachments);

			// commit it
			commitEvent(edit);

			return edit;

		} // addEvent

		/**
		 * Add a new event to this calendar. Must commitEvent() to make official, or cancelEvent() when done!
		 * 
		 * @return The newly added event, locked for update.
		 * @exception PermissionException
		 *            If the user does not have write permission to the calendar.
		 */
		public CalendarEventEdit addEvent() throws PermissionException
		{
			// check security (throws if not permitted)
			unlock(AUTH_ADD_CALENDAR, getReference());

			// allocate a new unique event id
			String id = getUniqueId();

			// get a new event in the info store
			CalendarEventEdit event = m_storage.putEvent(this, id);

			((BaseCalendarEventEdit) event).setEvent(EVENT_ADD_CALENDAR);

			return event;

		} // addEvent

		/**
		 * Merge in a new event as defined in the xml.
		 * 
		 * @param el
		 *        The event information in XML in a DOM element.
		 * @exception PermissionException
		 *            If the user does not have write permission to the calendar.
		 * @exception IdUsedException
		 *            if the user id is already used.
		 */
		public CalendarEventEdit mergeEvent(Element el) throws PermissionException, IdUsedException
		{
			CalendarEvent eventFromXml = (CalendarEvent) newResource(this, el);

			// check security 
         if ( ! allowAddEvent() )
			   throw new PermissionException(sessionManager.getCurrentSessionUserId(),
                                          AUTH_ADD_CALENDAR, getReference());
			// reserve a calendar event with this id from the info store - if it's in use, this will return null
			CalendarEventEdit event = m_storage.putEvent(this, eventFromXml.getId());
			if (event == null)
			{
				throw new IdUsedException(eventFromXml.getId());
			}

			// transfer from the XML read object to the Edit
			((BaseCalendarEventEdit) event).set(eventFromXml);

			((BaseCalendarEventEdit) event).setEvent(EVENT_MODIFY_CALENDAR);

			return event;

		} // mergeEvent

		/**
		 * check permissions for removeEvent().
		 * 
		 * @param event
		 *        The event from this calendar to remove.
		 * @return true if the user is allowed to removeEvent(event), false if not.
		 */
		public boolean allowRemoveEvent(CalendarEvent event)
		{
         boolean allowed = false;
         boolean ownEvent = event.isUserOwner();
         
			// check security to delete any event
         if ( unlockCheck(AUTH_REMOVE_CALENDAR_ANY, getReference()) )
            allowed = true;
            
			// check security to delete own event
			else if ( unlockCheck(AUTH_REMOVE_CALENDAR_OWN, getReference()) && ownEvent )
            allowed = true; 
            
			// but we must also assure, that for grouped events, we can remove it from ALL of the groups
			if (allowed && (event.getAccess() == EventAccess.GROUPED))
			{
				allowed = EntityCollections.isContainedEntityRefsToEntities(event.getGroups(), getGroupsAllowRemoveEvent(ownEvent));
			}

			return allowed;

		} // allowRemoveEvent

		/**
		 * Remove an event from the calendar, one locked for edit. Note: if the event is a recurring event, the entire sequence is modified by this commit (MOD_ALL).
		 * 
		 * @param edit
		 *        The event from this calendar to remove.
		 */
		public void removeEvent(CalendarEventEdit edit) throws PermissionException
		{
			removeEvent(edit, MOD_ALL);

		} // removeEvent

		/**
		 * Remove an event from the calendar, one locked for edit.
		 * 
		 * @param edit
		 *        The event from this calendar to remove.
		 * @param intention
		 *        The recurring event modification intention, based on values in the CalendarService "MOD_*", used if the event is part of a recurring event sequence to determine how much of the sequence is removed.
		 */
		public void removeEvent(CalendarEventEdit edit, int intention) throws PermissionException
		{
			// check for closed edit
			if (!edit.isActiveEdit())
			{
				try
				{
					throw new Exception();
				}
				catch (Exception e)
				{
					log.warn("removeEvent(): closed EventEdit", e);
				}
				return;
			}

			// securityCheck
			if (!allowRemoveEvent(edit))
			{
				cancelEvent(edit);
				throw new PermissionException(sessionManager.getCurrentSessionUserId(), AUTH_REMOVE_CALENDAR_ANY, edit.getReference());
			}

			BaseCalendarEventEdit bedit = (BaseCalendarEventEdit) edit;

			// if the id has a time range encoded, as for one of a sequence of recurring events, separate that out
			String indivEventEntityRef = null;
			TimeRange timeRange = null;
			int sequence = 0;
			if (bedit.m_id.startsWith("!"))
			{
				indivEventEntityRef = bedit.getReference();
				String[] parts = bedit.m_id.substring(1).split("!");
				try
				{
					timeRange = timeService.newTimeRange(parts[0]);
					sequence = Integer.parseInt(parts[1]);
					bedit.m_id = parts[2];
				}
				catch (Exception ex)
				{
					log.warn("removeEvent: exception parsing eventId: " + bedit.m_id + " : " + ex);
				}
			}

			// deal with recurring event sequence modification
			if (timeRange != null)
			{
				// delete only this - add it as an exclusion in the edit
				if (intention == MOD_THIS)
				{
					// get the edit back to initial values... so only the exclusion is changed
					edit = (CalendarEventEdit) m_storage.getEvent(this, bedit.m_id);
					bedit = (BaseCalendarEventEdit) edit;

					// add an exclusion for where this one would have been %%% we are changing it, should it be immutable? -ggolden
					List exclusions = ((ExclusionSeqRecurrenceRule) bedit.getExclusionRule()).getExclusions();
					exclusions.add(sequence);

					// complete the edit
					m_storage.commitEvent(this, edit);
					// post event for excluding the instance
					eventTrackingService.post(eventTrackingService.newEvent(EVENT_MODIFY_CALENDAR_EVENT_EXCLUSIONS, indivEventEntityRef, true));
				}

				// delete them all, i.e. the one initial event
				else
				{
					m_storage.removeEvent(this, edit);
					eventTrackingService.post(eventTrackingService.newEvent(EVENT_REMOVE_CALENDAR_EVENT, edit.getReference(), true));
				}
			}

			// else a single event to delete
			else
			{
				m_storage.removeEvent(this, edit);
				eventTrackingService.post(eventTrackingService.newEvent(EVENT_REMOVE_CALENDAR_EVENT, edit.getReference(), true));
			}

			// track event
			Event event = eventTrackingService.newEvent(EVENT_MODIFY_CALENDAR, edit.getReference(), true);
			eventTrackingService.post(event);
			
			// calendar notification
			notify(event);

			// close the edit object
			((BaseCalendarEventEdit) edit).closeEdit();

			// remove any realm defined for this resource
			try
			{
				authzGroupService.removeAuthzGroup(authzGroupService.getAuthzGroup(edit.getReference()));
			}
			catch (AuthzPermissionException e)
			{
				log.warn("removeEvent: removing realm for : " + edit.getReference() + " : " + e);
			}
			catch (GroupNotDefinedException gnde)
			{
				log.debug(gnde.getMessage());
			}
			catch (AuthzRealmLockException arle)
			{
				log.warn("GROUP LOCK REGRESSION: {}", arle.getMessage(), arle);
			}

		} // removeEvent

		/**
		 * check permissions for editEvent()
		 * 
		 * @param eventId
		 *        The event id.
		 * @return true if the user is allowed to update the event, false if not.
		 */
		public boolean allowEditEvent(String eventId)
		{
			CalendarEvent e = findEvent(eventId);
			if (e == null) return false;

         boolean ownEvent = e.isUserOwner();
         
			// check security to revise any event
			if ( unlockCheck(AUTH_MODIFY_CALENDAR_ANY, getReference()) )
            return true;
            
			// check security to revise own event
			else if ( unlockCheck(AUTH_MODIFY_CALENDAR_OWN, getReference()) && ownEvent )
            return true;
            
         // otherwise not authorized
         else
            return false;

		} // allowEditEvent

		/**
		 * Return a specific calendar event, as specified by event name, locked for update. 
		 * Must commitEvent() to make official, or cancelEvent(), or removeEvent() when done!
		 * 
		 * @param eventId  The id of the event to get.
		 * @param editType add, remove or modifying calendar?
		 * @return the Event that has the specified id.
		 * @exception IdUnusedException
		 *            If this name is not a defined event in this calendar.
		 * @exception PermissionException
		 *            If the user does not have any permissions to edit the event.
		 * @exception InUseException
		 *            if the event is locked for edit by someone else.
		 */
		@Override
		public CalendarEventEdit getEditEvent(String eventId, String editType)
			throws IdUnusedException, PermissionException, InUseException
		{
			// if the id has a time range encoded, as for one of a sequence of recurring events, separate that out
			TimeRange timeRange = null;
			int sequence = 0;
			if (eventId.startsWith("!"))
			{
				String[] parts = eventId.substring(1).split("!");
				try
				{
					timeRange = timeService.newTimeRange(parts[0]);
					sequence = Integer.parseInt(parts[1]);
					eventId = parts[2];
				}
				catch (Exception ex)
				{
					log.warn("getEditEvent: exception parsing eventId: {} : {}", eventId, ex.toString());
				}
			}

			CalendarEvent e = findEvent(eventId);
			if (e == null) throw new IdUnusedException(eventId);

			// check security 
         if ( editType.equals(EVENT_ADD_CALENDAR) && ! allowAddEvent() )
			   throw new PermissionException(sessionManager.getCurrentSessionUserId(),
                                          AUTH_ADD_CALENDAR, getReference());
         else if ( editType.equals(EVENT_REMOVE_CALENDAR) && ! allowRemoveEvent(e) )
			   throw new PermissionException(sessionManager.getCurrentSessionUserId(),
                                          AUTH_REMOVE_CALENDAR_ANY, getReference());
         else if ( editType.equals(EVENT_MODIFY_CALENDAR) && ! allowEditEvent(eventId) )
			   throw new PermissionException(sessionManager.getCurrentSessionUserId(),
                                          AUTH_MODIFY_CALENDAR_ANY, getReference());

			// ignore the cache - get the CalendarEvent with a lock from the info store
			CalendarEventEdit edit = m_storage.editEvent(this, eventId);
			if (edit == null) throw new InUseException(eventId);

			BaseCalendarEventEdit bedit = (BaseCalendarEventEdit) edit;

			// if this is one in a sequence, adjust it
			if (timeRange != null)
			{
				// move the specified range into the event's range, storing the base range
				bedit.m_baseRange = bedit.m_range;
				bedit.m_range = timeRange;
				bedit.m_id = '!' + bedit.m_range.toString() + '!' + sequence + '!' + bedit.m_id;
			}

			bedit.setEvent(EVENT_MODIFY_CALENDAR);

			return edit;

		} // getEditEvent

		/**
		 * Commit the changes made to a CalendarEventEdit object, and release the lock. The CalendarEventEdit is disabled, and not to be used after this call. Note: if the event is a recurring event, the entire sequence is modified by this commit
		 * (MOD_ALL).
		 * 
		 * @param edit
		 *        The CalendarEventEdit object to commit.
		 */
		public void commitEvent(CalendarEventEdit edit)
		{
			commitEvent(edit, MOD_ALL);

		} // commitEvent

		/**
		 * Commit the changes made to a CalendarEventEdit object, and release the lock. The CalendarEventEdit is disabled, and not to be used after this call.
		 * 
		 * @param edit
		 *        The CalendarEventEdit object to commit.
		 * @param intention
		 *        The recurring event modification intention, based on values in the CalendarService "MOD_*", used if the event is part of a recurring event sequence to determine how much of the sequence is changed by this commmit.
		 */
		public void commitEvent(CalendarEventEdit edit, int intention)
		{
			// check for closed edit
			if (!edit.isActiveEdit())
			{
				log.warn("commitEvent(): closed CalendarEventEdit {}", edit.getId());
				return;
			}

			BaseCalendarEventEdit bedit = (BaseCalendarEventEdit) edit;

			// If creator doesn't exist, set it now (backward compatibility)
			if ( edit.getCreator() == null || edit.getCreator().isEmpty())
				edit.setCreator(); 

			// update modified-by properties for event
			edit.setModifiedBy(); 

			// if the id has a time range encoded, as for one of a sequence of recurring events, separate that out
			String indivEventEntityRef = null;
			TimeRange timeRange = null;
			int sequence = 0;
			if (bedit.m_id.startsWith("!"))
			{
				indivEventEntityRef = bedit.getReference();
				String[] parts = bedit.m_id.substring(1).split("!");
				try
				{
					timeRange = timeService.newTimeRange(parts[0]);
					sequence = Integer.parseInt(parts[1]);
					bedit.m_id = parts[2];
				}
				catch (Exception ex)
				{
					log.warn("commitEvent: exception parsing eventId: " + bedit.m_id + " : " + ex);
				}
			}

			// for recurring event sequence
			TimeRange newTimeRange = null;
			BaseCalendarEventEdit newEvent = null;
			if (timeRange != null)
			{
				// if changing this event only
				if (intention == MOD_THIS)
				{
					// make a new event for this one
					String id = getUniqueId();
					newEvent = (BaseCalendarEventEdit) m_storage.putEvent(this, id);
					newEvent.setPartial(edit);
					m_storage.commitEvent(this, newEvent);
					eventTrackingService.post(eventTrackingService.newEvent(EVENT_MODIFY_CALENDAR, newEvent.getReference(), true));
					eventTrackingService.post(eventTrackingService.newEvent(EVENT_MODIFY_CALENDAR_EVENT_EXCLUDED, newEvent.getReference(), true));
					eventTrackingService.post(eventTrackingService.newEvent(EVENT_MODIFY_CALENDAR_EVENT_EXCLUSIONS, indivEventEntityRef, true));

					// get the edit back to initial values... so only the exclusion is changed
					edit = (CalendarEventEdit) m_storage.getEvent(this, bedit.m_id);
					bedit = (BaseCalendarEventEdit) edit;

					// add an exclusion for where this one would have been %%% we are changing it, should it be immutable? -ggolden
					List exclusions = ((ExclusionSeqRecurrenceRule) bedit.getExclusionRule()).getExclusions();
					exclusions.add(Integer.valueOf(sequence));
				}

				// else change the entire sequence (i.e. the one initial event)
				else
				{
					// the time range may have been modified in the edit
					newTimeRange = bedit.m_range;

					// restore the real range, that of the base event of a sequence, if this is one of the other events in the sequence.
					bedit.m_range = bedit.m_baseRange;

					// adjust the base range if there was an edit to range
					bedit.m_range.adjust(timeRange, newTimeRange);
					
				}
			}

			// update the properties
			// addLiveUpdateProperties(edit.getPropertiesEdit());//%%%
			
			// complete the edit
			m_storage.commitEvent(this, edit);

			// track event
			Event event = eventTrackingService.newEvent(bedit.getEvent(), edit.getReference(), true);
			eventTrackingService.post(event);

			// calendar notification
			notify(event);

			// close the edit object
			bedit.closeEdit();
			
			// Update modify time on calendar
			this.setModified();
			m_storage.commitCalendar(this);

			// restore this one's range etc so it can be further referenced
			if (timeRange != null)
			{
				// if changing this event only
				if (intention == MOD_THIS)
				{
					// set the edit to the values of the new event
					bedit.set(newEvent);
				}

				// else we changed the sequence
				else
				{
					// move the specified range into the event's range, storing the base range
					bedit.m_baseRange = bedit.m_range;
					bedit.m_range = newTimeRange;
					bedit.m_id = '!' + bedit.m_range.toString() + '!' + sequence + '!' + bedit.m_id;
				}
			}

		} // commitEvent

		/**
		 * Cancel the changes made to a CalendarEventEdit object, and release the lock. The CalendarEventEdit is disabled, and not to be used after this call.
		 * 
		 * @param edit
		 *        The CalendarEventEdit object to commit.
		 */
		public void cancelEvent(CalendarEventEdit edit)
		{
			// check for closed edit
			if (!edit.isActiveEdit())
			{
				Throwable e = new Throwable();
				log.warn("cancelEvent(): closed CalendarEventEdit", e);
				return;
			}

			BaseCalendarEventEdit bedit = (BaseCalendarEventEdit) edit;

			// if the id has a time range encoded, as for one of a sequence of recurring events, separate that out
			TimeRange timeRange = null;
			int sequence = 0;
			if (bedit.m_id.startsWith("!"))
			{
				String[] parts = bedit.m_id.substring(1).split( "!");
				try
				{
					timeRange = timeService.newTimeRange(parts[0]);
					sequence = Integer.parseInt(parts[1]);
					bedit.m_id = parts[2];
				}
				catch (Exception ex)
				{
					log.warn("commitEvent: exception parsing eventId: " + bedit.m_id + " : " + ex);
				}
			}

			// release the edit lock
			m_storage.cancelEvent(this, edit);

			// close the edit object
			((BaseCalendarEventEdit) edit).closeEdit();

		} // cancelCalendarEvent

		/**
		 * Return the extra fields kept for each event in this calendar.
		 * 
		 * @return the extra fields kept for each event in this calendar, formatted into a single string. %%%
		 */
		public String getEventFields()
		{
			return m_properties.getPropertyFormatted(ResourceProperties.PROP_CALENDAR_EVENT_FIELDS);

		} // getEventFields

		/**
		 * Set the extra fields kept for each event in this calendar.
		 * 
		 * @param fields
		 *        The extra fields kept for each event in this calendar, formatted into a single string. %%%
		 */
		public void setEventFields(String fields)
		{
			m_properties.addProperty(ResourceProperties.PROP_CALENDAR_EVENT_FIELDS, fields);

		} // setEventFields

		/**
		 * Notify the calendar that it has changed
		 * 
		 * @param event
		 *        The event that caused the update.
		 */
		public void notify(Event event)
		{
			// notify observers, sending the tracking event to identify the change
			setChanged();
			notifyObservers(event);

		} // notify

		/**
		 * Serialize the resource into XML, adding an element to the doc under the top of the stack element.
		 * 
		 * @param doc
		 *        The DOM doc to contain the XML (or null for a string return).
		 * @param stack
		 *        The DOM elements, the top of which is the containing element of the new "resource" element.
		 * @return The newly added element.
		 */
		public Element toXml(Document doc, Stack stack)
		{
			Element calendar = doc.createElement("calendar");

			if (stack.isEmpty())
			{
				doc.appendChild(calendar);
			}
			else
			{
				((Element) stack.peek()).appendChild(calendar);
			}

			stack.push(calendar);

			calendar.setAttribute("context", m_context);
			calendar.setAttribute("id", m_id);

			// properties
			m_properties.toXml(doc, stack);

			stack.pop();

			return calendar;

		} // toXml

		/**
		 * Find the event, in cache or info store - cache it if newly found.
		 * 
		 * @param eventId
		 *        The id of the event.
		 * @return The event, if found.
		 */
		protected CalendarEvent findEvent(String eventId)
		{
			// if the id has a time range encoded, as for one of a sequence of recurring events, separate that out
			TimeRange timeRange = null;
			int sequence = 0;
			if (eventId.startsWith("!"))
			{
				String[] parts = eventId.substring(1).split("!");
				try
				{
					timeRange = timeService.newTimeRange(parts[0]);
					sequence = Integer.parseInt(parts[1]);
					eventId = parts[2];
				}
				catch (Exception ex)
				{
					log.warn("findEvent: exception parsing eventId: " + eventId + " : " + ex);
				}
			}

			CalendarEvent e = m_storage.getEvent(this, eventId);

			// now we have the primary event, if we have a recurring event sequence time range selector, use it
			if ((e != null) && (timeRange != null))
			{
				e = new BaseCalendarEventEdit(e, new RecurrenceInstance(timeRange, sequence));
			}

			return e;

		} // findEvent

		/**
		 * Access the event code for this edit.
		 * 
		 * @return The event code for this edit.
		 */
		protected String getEvent()
		{
			return m_event;
		}

		/**
		 * Set the event code for this edit.
		 * 
		 * @param event
		 *        The event code for this edit.
		 */
		protected void setEvent(String event)
		{
			m_event = event;
		}

		/**
		 * Access the resource's properties for modification
		 * 
		 * @return The resource's properties.
		 */
		public ResourcePropertiesEdit getPropertiesEdit()
		{
			return m_properties;

		} // getPropertiesEdit

		/**
		 * Enable editing.
		 */
		protected void activate()
		{
			m_active = true;

		} // activate

		/**
		 * Check to see if the edit is still active, or has already been closed.
		 * 
		 * @return true if the edit is active, false if it's been closed.
		 */
		public boolean isActiveEdit()
		{
			return m_active;

		} // isActiveEdit

		/**
		 * Close the edit object - it cannot be used after this.
		 */
		protected void closeEdit()
		{
			m_active = false;

		} // closeEdit
		
		@Override
		public Collection getGroupsAllowAddEvent() 
		{
			return getGroupsAllowFunction(AUTH_ADD_CALENDAR);
		}

		@Override
		public Collection getGroupsAllowGetEvent() 
		{
			return getGroupsAllowFunction(AUTH_READ_CALENDAR);
		}
		
		@Override
		public Collection getGroupsAllowRemoveEvent( boolean own ) 
		{
         return getGroupsAllowFunction(own ? AUTH_REMOVE_CALENDAR_OWN : AUTH_REMOVE_CALENDAR_ANY );
		}

		/**
		 * Get the groups of this channel's contex-site that the end user has permission to "function" in.
		 * @param function The function to check
		 */
		protected Collection getGroupsAllowFunction(String function)
		{
			Vector rv = new Vector();

			try
			{
				// get the channel's site's groups
				Site site = siteService.getSite(m_context);
				Collection groups = site.getGroups();

				// if the user has SECURE_ALL_GROUPS in the context (site), and the function for the calendar (calendar,site), select all site groups
				if ((securityService.isSuperUser()) || (securityService.unlock(sessionManager.getCurrentSessionUserId(), SECURE_ALL_GROUPS, siteService.siteReference(m_context))
						&& unlockCheck(function, getReference())))
				{
					rv.addAll( groups );
					Collections.sort( rv, groupComparator );
					return (Collection)rv;
				}
	
				// otherwise, check the groups for function

				// get a list of the group refs, which are authzGroup ids
				Collection groupRefs = new Vector();
				for (Iterator i = groups.iterator(); i.hasNext();)
				{
					Group group = (Group) i.next();
					groupRefs.add(group.getReference());
				}
			
				// ask the authzGroup service to filter them down based on function
				groupRefs = authzGroupService.getAuthzGroupsIsAllowed(sessionManager.getCurrentSessionUserId(), function, groupRefs);

				// pick the Group objects from the site's groups to return, those that are in the groupRefs list
				for (Iterator i = groups.iterator(); i.hasNext();)
				{
					Group group = (Group) i.next();
					if (groupRefs.contains(group.getReference()))
					{
						rv.add(group);
					}
				}
			}
			catch (IdUnusedException ignore) {}

			Collections.sort( rv, groupComparator );
			return (Collection)rv;
			
		} // getGroupsAllowFunction

		/******************************************************************************************************************************************************************************************************************************************************
		 * SessionBindingListener implementation
		 *****************************************************************************************************************************************************************************************************************************************************/

		public void valueBound(SessionBindingEvent event)
		{
		}

		public void valueUnbound(SessionBindingEvent event)
		{
			if (log.isDebugEnabled()) log.debug("valueUnbound()");

			// catch the case where an edit was made but never resolved
			if (m_active)
			{
				cancelCalendar(this);
			}

		} // valueUnbound

		/**
		 * Get a ContentHandler suitable for populating this object from SAX Events
		 * @return
		 */
		public ContentHandler getContentHandler(Map<String,Object> services)
		{
			final Entity thisEntity = this;
			return new DefaultEntityHandler() {
				/* (non-Javadoc)
				 * @see org.sakaiproject.util.DefaultEntityHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
				 */
				@Override
				public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
				{
					if ( doStartElement(uri, localName, qName, attributes) ) {
						if ( "calendar".equals(qName) && entity == null ) {
							m_id = attributes.getValue("id");
							m_context = attributes.getValue("context");	
							entity = thisEntity;
						} else {
							log.warn("Unexpected element "+qName);
						}
					}
				}
			};
		}

		/**
		 * Checks if user has permission to modify any event (or fields) in this calendar
		 * @param function
		 * @return
		 */
		@Override
		public boolean canModifyAnyEvent(String function) {
			return AUTH_MODIFY_CALENDAR_ANY.equals(function);		
		}
		
	} // class BaseCalendar

	
	/**********************************************************************************************************************************************************************************************************************************************************
	 * CalendarEvent implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	public class BaseCalendarEventEdit implements CalendarEventEdit, SessionBindingListener
	{
		/** The calendar in which this event lives. */
		protected BaseCalendarEdit m_calendar = null;

		/** The effective time range. */
		protected TimeRange m_range = null;

		/**
		 * The base time range: for non-recurring events, this matches m_range, but for recurring events, it is always the range of the initial event in the sequence (transient).
		 */
		protected TimeRange m_baseRange = null;

		/** The recurrence rule (single rule). */
		protected RecurrenceRule m_singleRule = null;

		/** The exclusion recurrence rule. */
		protected RecurrenceRule m_exclusionRule = null;

		/** The properties. */
		protected ResourcePropertiesEdit m_properties = null;

		/** The event id. */
		protected String m_id = null;

		/** The attachments - dereferencer objects. */
		protected List m_attachments = null;

		/** The event code for this edit. */
		protected String m_event = null;

		/** Active flag. */
		protected boolean m_active = false;
		
		/** The Collection of groups (authorization group id strings). */
		protected Collection m_groups = new Vector();
		
		/** The message access. */
		protected EventAccess m_access = EventAccess.SITE;

		protected String siteId;

		/**
		 * Construct.
		 * 
		 * @param calendar
		 *        The calendar in which this event lives.
		 * @param id
		 *        The event id, unique within the calendar.
		 */
		public BaseCalendarEventEdit(Calendar calendar, String id)
		{
			m_calendar = (BaseCalendarEdit) calendar;
			m_id = id;

			// setup for properties
			m_properties = new BaseResourcePropertiesEdit();

			// init the AttachmentContainer
			m_attachments = entityManager.newReferenceList();

		} // BaseCalendarEventEdit

		/**
		 * Construct as a copy of another event.
		 * 
		 * @param other
		 *        The other event to copy.
		 */
		public BaseCalendarEventEdit(Calendar calendar, CalendarEvent other)
		{
			// store the calendar
			m_calendar = (BaseCalendarEdit) calendar;

			set(other);

		} // BaseCalendarEventEdit

		/**
		 * Construct as a thin copy of another event, with this new time range, and no rules, as part of a recurring event sequence.
		 * 
		 * @param other
		 *        The other event to copy.
		 * @param ri
		 *        The RecurrenceInstance with the time range (and sequence number) to use.
		 */
		public BaseCalendarEventEdit(CalendarEvent other, RecurrenceInstance ri)
		{
			// store the calendar
			m_calendar = ((BaseCalendarEventEdit) other).m_calendar;

			// encode the instance and the other's id into my id
			m_id = '!' + ri.getRange().toString() + '!' + ri.getSequence() + '!' + ((BaseCalendarEventEdit) other).m_id;

			// use the new range
			m_range = (TimeRange) ri.getRange().clone();
			m_baseRange = ((BaseCalendarEventEdit) other).m_range;

			// point at the properties
			m_properties = ((BaseCalendarEventEdit) other).m_properties;
			
			m_access = ((BaseCalendarEventEdit) other).m_access;
			
			// point at the groups
			m_groups = ((BaseCalendarEventEdit) other).m_groups;

			// point at the attachments
			m_attachments = ((BaseCalendarEventEdit) other).m_attachments;

			// point at the rules
			m_singleRule = ((BaseCalendarEventEdit) other).m_singleRule;
			m_exclusionRule = ((BaseCalendarEventEdit) other).m_exclusionRule;

		} // BaseCalendarEventEdit

		/**
		 * Construct from an existing definition, in xml.
		 * 
		 * @param calendar
		 *        The calendar in which this event lives.
		 * @param el
		 *        The event in XML in a DOM element.
		 */
		public BaseCalendarEventEdit(Calendar calendar, Element el)
		{
			m_calendar = (BaseCalendarEdit) calendar;
			m_properties = new BaseResourcePropertiesEdit();
			m_attachments = entityManager.newReferenceList();

			m_id = el.getAttribute("id");
			m_range = timeService.newTimeRange(el.getAttribute("range"));
			
			m_access = CalendarEvent.EventAccess.SITE;
			String access_str = el.getAttribute("access").toString();
			if (access_str.equals(CalendarEvent.EventAccess.GROUPED.toString()))
				m_access = CalendarEvent.EventAccess.GROUPED;
					

			// the children (props / attachments / rules)
			NodeList children = el.getChildNodes();
			final int length = children.getLength();
			for (int i = 0; i < length; i++)
			{
				Node child = children.item(i);
				if (child.getNodeType() == Node.ELEMENT_NODE)
				{
					Element element = (Element) child;
					// look for an attachment
					if (element.getTagName().equals("attachment"))
					{
						m_attachments.add(entityManager.newReference(element.getAttribute("relative-url")));
					}

					// look for properties
					else if (element.getTagName().equals("properties"))
					{
						// re-create properties
						m_properties = new BaseResourcePropertiesEdit(element);
					}

					else if (element.getTagName().equals("group"))
					{
						m_groups.add(element.getAttribute("authzGroup"));
					}
					
					// else look for rules
					else if (element.getTagName().equals("rules"))
					{
						// children are "rule" elements
						NodeList ruleChildren = element.getChildNodes();
						final int ruleChildrenLength = ruleChildren.getLength();
						for (int iRuleChildren = 0; iRuleChildren < ruleChildrenLength; iRuleChildren++)
						{
							Node ruleChildNode = ruleChildren.item(iRuleChildren);
							if (ruleChildNode.getNodeType() == Node.ELEMENT_NODE)
							{
								Element ruleChildElement = (Element) ruleChildNode;

								// look for a rule or exclusion rule
								if (ruleChildElement.getTagName().equals("rule") || ruleChildElement.getTagName().equals("ex-rule"))
								{
									// get the rule name - modern style encoding
									String ruleName = StringUtils.trimToNull(ruleChildElement.getAttribute("name"));

									// deal with old data
									if (ruleName == null)
									{
										// get the class - this is old CHEF 1.2.10 style encoding
										String ruleClassOld = ruleChildElement.getAttribute("class");

										// use the last class name minus the package
										if ( ruleClassOld != null )
											ruleName = ruleClassOld.substring(ruleClassOld.lastIndexOf('.') + 1);
										if ( ruleName == null )
											log.warn("trouble loading rule");
									}

									if (ruleName != null)
									{
										// put my package on the class name
										String ruleClass = this.getClass().getPackage().getName() + "." + ruleName;

										// construct
										try
										{
											if (ruleChildElement.getTagName().equals("rule"))
											{
												m_singleRule = (RecurrenceRule) Class.forName(ruleClass).newInstance();
												m_singleRule.set(ruleChildElement);
											}
											else //	ruleChildElement.getTagName().equals("ex-rule"))
											{
												m_exclusionRule = (RecurrenceRule) Class.forName(ruleClass).newInstance();
												m_exclusionRule.set(ruleChildElement);
											}
										}
										catch (Exception e)
										{
											log.warn("trouble loading rule: " + ruleClass + " : " + e);
										}
									}
								}
							}
						}
					}
				}
			}

		} // BaseCalendarEventEdit

		/**
		 * 
		 */
		public BaseCalendarEventEdit(Entity container)
		{
			m_calendar = (BaseCalendarEdit) container;

			m_properties = new BaseResourcePropertiesEdit();
			m_attachments = entityManager.newReferenceList();

		}


		/**
		 * Take all values from this object.
		 * 
		 * @param other
		 *        The other object to take values from.
		 */
		protected void set(CalendarEvent other)
		{
			// copy the id
			m_id = other.getId();

			// copy the range
			m_range = (TimeRange) other.getRange().clone();

			// copy the properties
			m_properties = new BaseResourcePropertiesEdit();
			m_properties.addAll(other.getProperties());
			
			m_access = other.getAccess();
			m_groups = new Vector();
			m_groups.addAll(other.getGroups());

			// copy the attachments
			m_attachments = entityManager.newReferenceList();
			replaceAttachments(other.getAttachments());

			// copy the rules
			// %%% deep enough? -ggolden
			m_singleRule = ((BaseCalendarEventEdit) other).m_singleRule;
			m_exclusionRule = ((BaseCalendarEventEdit) other).m_exclusionRule;

		} // set

		/**
		 * Take some values from this object (not id, not rules).
		 * 
		 * @param other
		 *        The other object to take values from.
		 */
		protected void setPartial(CalendarEvent other)
		{
			// copy the range
			m_range = (TimeRange) other.getRange().clone();

			// copy the properties
			m_properties = new BaseResourcePropertiesEdit();
			m_properties.addAll(other.getProperties());
			
			m_access = other.getAccess();
			m_groups = new Vector();
			m_groups.addAll(other.getGroups());
			
			// copy the attachments
			m_attachments = entityManager.newReferenceList();
			replaceAttachments(other.getAttachments());

		} // setPartial

		/**
		 * Access the time range
		 * 
		 * @return The event time range
		 */
		public TimeRange getRange()
		{
			// range might be null in the creation process, before the fields are set in an edit, but
			// after the storage has registered the event and it's id.
			if (m_range == null)
			{
				return timeService.newTimeRange(timeService.newTime(0));
			}

			// return (TimeRange) m_range.clone();
			return m_range;
		} // getRange

		/**
		 * Replace the time range
		 * 
		 * @param range
		 *        new event time range
		 */
		public void setRange(TimeRange range)
		{
			m_range = (TimeRange) range.clone();

		} // setRange

		/**
		 * Access the display name property (cover for PROP_DISPLAY_NAME).
		 * 
		 * @return The event's display name property.
		 */
		public String getDisplayName()
		{
			return m_properties.getPropertyFormatted(ResourceProperties.PROP_DISPLAY_NAME);

		} // getDisplayName

		/**
		 * Set the display name property (cover for PROP_DISPLAY_NAME).
		 * 
		 * @param name
		 *        The event's display name property.
		 */
		public void setDisplayName(String name)
		{
			m_properties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, name);

		} // setDisplayName

		/**
		 * Access the description property as plain text.
		 * 
		 * @return The event's description property.
		 */
		public String getDescription()
		{
			return FormattedText.convertFormattedTextToPlaintext(getDescriptionFormatted());
		}

		/**
		 * Access the description property as formatted text.
		 * 
		 * @return The event's description property.
		 */
		public String getDescriptionFormatted()
		{
			// %%% JANDERSE the calendar event description can now be formatted text
			// first try to use the formatted text description; if that isn't found, use the plaintext description
			String desc = m_properties.getPropertyFormatted(ResourceProperties.PROP_DESCRIPTION + "-html");
			if (desc != null && desc.length() > 0) return desc;
			desc = m_properties.getPropertyFormatted(ResourceProperties.PROP_DESCRIPTION + "-formatted");
			desc = FormattedText.convertOldFormattedText(desc);
			if (desc != null && desc.length() > 0) return desc;
			desc = FormattedText.convertPlaintextToFormattedText(m_properties
					.getPropertyFormatted(ResourceProperties.PROP_DESCRIPTION));
			return desc;
		} // getDescriptionFormatted()

		/**
		 * Set the description property as plain text.
		 * 
		 * @param description
		 *        The event's description property.
		 */
		public void setDescription(String description)
		{
			setDescriptionFormatted(FormattedText.convertPlaintextToFormattedText(description));
		}

		/**
		 * Set the description property as formatted text.
		 * 
		 * @param description
		 *        The event's description property.
		 */
		public void setDescriptionFormatted(String description)
		{
			// %%% JANDERSE the calendar event description can now be formatted text
			// save both a formatted and a plaintext version of the description
			m_properties.addProperty(ResourceProperties.PROP_DESCRIPTION + "-html", description);
			m_properties.addProperty(ResourceProperties.PROP_DESCRIPTION, FormattedText
					.convertFormattedTextToPlaintext(description));
		} // setDescriptionFormatted()

		/**
		 * Access the type (cover for PROP_CALENDAR_TYPE).
		 * 
		 * @return The event's type property.
		 */
		public String getType()
		{
			return m_properties.getPropertyFormatted(ResourceProperties.PROP_CALENDAR_TYPE);

		} // getType

		/**
		 * Set the type (cover for PROP_CALENDAR_TYPE).
		 * 
		 * @param type
		 *        The event's type property.
		 */
		public void setType(String type)
		{
			m_properties.addProperty(ResourceProperties.PROP_CALENDAR_TYPE, type);

		} // setType

		/**
		 * Access the location (cover for PROP_CALENDAR_LOCATION).
		 * 
		 * @return The event's location property.
		 */
		public String getLocation()
		{
			return m_properties.getPropertyFormatted(ResourceProperties.PROP_CALENDAR_LOCATION);

		} // getLocation

		/**
		 * Access the siteId
		 * 
		 * @return The event's siteId
		 */
		public String getSiteId()
		{
			return m_properties.getPropertyFormatted(ResourceProperties.PROP_CALENDAR_SITE_ID);
		}

		/**
		 * Access the event url (cover for PROP_CALENDAR_URL).
		 *
		 * @return The event's eventUrl property.
		 */
		public String getEventUrl()
		{
			return m_properties.getPropertyFormatted(ResourceProperties.PROP_CALENDAR_URL);

		}

		/**
		 * Gets the recurrence rule, if any.
		 * 
		 * @return The recurrence rule, or null if none.
		 */
		public RecurrenceRule getRecurrenceRule()
		{
			return m_singleRule;

		} // getRecurrenceRule

		/**
		 * Gets the exclusion recurrence rule, if any.
		 * 
		 * @return The exclusionrecurrence rule, or null if none.
		 */
		public RecurrenceRule getExclusionRule()
		{
			if (m_exclusionRule == null) m_exclusionRule = new ExclusionSeqRecurrenceRule();

			return m_exclusionRule;

		} // getExclusionRule

		/*
		 * Return a list of all resolved events generated from this event plus it's recurrence rules that fall within the time range, including this event, possibly empty.
		 * 
		 * @param range
		 *        The time range bounds for the events returned.
		 * @return a List (CalendarEvent) of all events and recurrences within the time range, including this, possibly empty.
		 */
		protected List resolve(TimeRange range)
		{
			List rv = new Vector();

			// for no rules, use the event if it's in range
			if (m_singleRule == null)
			{
				// the actual event
				if (range.overlaps(getRange()))
				{
					rv.add(this);
				}
			}

			// for rules...
			else
			{
				
				// for a re-occurring event, the time zone where the first event is created
				// is passed as a parameter (timezone) to correctly generate the instances 
				String timeZoneID = this.getField("createdInTimeZone");
				TimeZone timezone = null;
				if (timeZoneID.equals(""))
				{
					timezone = timeService.getLocalTimeZone();
				}
				else
				{
					timezone = TimeZone.getTimeZone(timeZoneID);
				}
				List instances = m_singleRule.generateInstances(this.getRange(), range, timezone);

				// remove any excluded
				getExclusionRule().excludeInstances(instances);

				for (Iterator iRanges = instances.iterator(); iRanges.hasNext();)
				{
					RecurrenceInstance ri = (RecurrenceInstance) iRanges.next();

					// generate an event object that is exactly like me but with this range and no rules
					CalendarEvent clone = new BaseCalendarEventEdit(this, ri);

					rv.add(clone);
				}
			}

			return rv;

		} // resolve

		/**
		 * Get the value of an "extra" event field.
		 * 
		 * @param name
		 *        The name of the field.
		 * @return the value of the "extra" event field.
		 */
		public String getField(String name)
		{
			name = FormattedText.unEscapeHtml(name);
			// names are prefixed to form a namespace
			name = ResourceProperties.PROP_CALENDAR_EVENT_FIELDS + "." + name;

			return m_properties.getPropertyFormatted(name);

		} // getField

		/**
		 * Set the value of an "extra" event field.
		 * 
		 * @param name
		 *        The "extra" field name
		 * @param value
		 *        The value to set, or null to remove the field.
		 */
		public void setField(String name, String value)
		{
			// names are prefixed to form a namespace
			name = ResourceProperties.PROP_CALENDAR_EVENT_FIELDS + "." + name;

			if (value == null)
			{
				m_properties.removeProperty(name);
			}
			else
			{
				m_properties.addProperty(name, value);
			}

		} // setField

		/**
		 * Set the location (cover for PROP_CALENDAR_LOCATION).
		 * 
		 * @param location
		 *        The event's location property.
		 */
		public void setLocation(String location)
		{
			m_properties.addProperty(ResourceProperties.PROP_CALENDAR_LOCATION, location);

		}

		/**
		 * Set the siteId (cover for PROP_CALENDAR_SITE_ID).
		 * 
		 * @param siteId
		 *        The event's siteId property.
		 */
		public void setSiteId(String siteId)
		{
			m_properties.addProperty(ResourceProperties.PROP_CALENDAR_SITE_ID, siteId);
		}

		public void setEventUrl(String url)
		{
			m_properties.addProperty(ResourceProperties.PROP_CALENDAR_URL, url);
		}

		/**
		* Gets the event creator (userid), if any (cover for PROP_CREATOR).
		* @return The event's creator property.
		*/
		public String getCreator()
		{
			return m_properties.getProperty(ResourceProperties.PROP_CREATOR);

		} // getCreator

		/**
		* Returns true if current user is thhe event's owner/creator
		* @return boolean true or false
		*/
		public boolean isUserOwner()
		{
			String currentUser = sessionManager.getCurrentSessionUserId();
			String eventOwner = this.getCreator();
                   
			// for backward compatibility, treat unowned event as if it owned by this user
			return (eventOwner == null || eventOwner.equals("") || (currentUser != null && currentUser.equals(eventOwner)) );
		}

		/**
		* Set the event creator (cover for PROP_CREATOR) to current user
		*/
		public void setCreator()
		{
			String currentUser = sessionManager.getCurrentSessionUserId();
			String now = timeService.newTime().toString();
			m_properties.addProperty(ResourceProperties.PROP_CREATOR, currentUser);
			m_properties.addProperty(ResourceProperties.PROP_CREATION_DATE, now);

		} // setCreator

		/**
		* Gets the event modifier (userid), if any (cover for PROP_MODIFIED_BY).
		* @return The event's modified-by property.
		*/
		public String getModifiedBy()
		{
			return m_properties.getPropertyFormatted(ResourceProperties.PROP_MODIFIED_BY);

		} // getModifiedBy

		/**
		* Set the event modifier (cover for PROP_MODIFIED_BY) to current user
		*/
		public void setModifiedBy()
		{
			String currentUser = sessionManager.getCurrentSessionUserId();
			String now = timeService.newTime().toString();
			m_properties.addProperty(ResourceProperties.PROP_MODIFIED_BY, currentUser);
			m_properties.addProperty(ResourceProperties.PROP_MODIFIED_DATE, now);

		} // setModifiedBy

		/**
		 * Sets the recurrence rule.
		 * 
		 * @param rule
		 *        The recurrence rule, or null to clear out the rule.
		 */
		public void setRecurrenceRule(RecurrenceRule rule)
		{
			m_singleRule = rule;

		} // setRecurrenceRule

		/**
		 * Sets the exclusion recurrence rule.
		 * 
		 * @param rule
		 *        The recurrence rule, or null to clear out the rule.
		 */
		public void setExclusionRule(RecurrenceRule rule)
		{
			m_exclusionRule = rule;

		} // setExclusionRule

		/**
		 * Access the id of the resource.
		 * 
		 * @return The id.
		 */
		public String getId()
		{
			return m_id;

		} // getId

		/**
		 * Access the URL which can be used to access the resource.
		 * 
		 * @return The URL which can be used to access the resource.
		 */
		public String getUrl()
		{
			return m_calendar.getUrl() + getId();

		} // getUrl

		/**
		 * Access the internal reference which can be used to access the resource from within the system.
		 * 
		 * @return The the internal reference which can be used to access the resource from within the system.
		 */
		public String getReference()
		{
			return eventReference(m_calendar.getContext(), m_calendar.getId(), getId());

		} // getReference

		@Override
		public String getReference(String rootProperty)
		{
			return getReference();
		}

		@Override
		public String getUrl(String rootProperty)
		{
			return getUrl();
		}

		/**
		 * Access the event's properties.
		 * 
		 * @return The event's properties.
		 */
		public ResourceProperties getProperties()
		{
			return m_properties;

		} // getProperties

		/**
		 * Gets a site name for this calendar event
		 */
		public String getSiteName()
		{
			String calendarName = "";
			
			if (m_calendar != null)
			{
				try
				{
					Site site = siteService.getSite(m_calendar.getContext());
					if (site != null)
						calendarName = site.getTitle();
				}
				catch (IdUnusedException e)
				{
					log.warn(".getSiteName(): " + e);
				}
			}
			
			return calendarName;
		}
		
		/**
		 * Notify the event that it has changed.
		 * 
		 * @param event
		 *        The event that caused the update.
		 */
		public void notify(Event event)
		{
			m_calendar.notify(event);

		} // notify

		/**
		 * Compare one event to another, based on range.
		 * 
		 * @param o
		 *        The object to be compared.
		 * @return A negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
		 */
		public int compareTo(Object o)
		{
			if (!(o instanceof CalendarEvent)) throw new ClassCastException();
			Time mine = getRange().firstTime();
			Time other = ((CalendarEvent) o).getRange().firstTime();

			if (mine.before(other)) return -1;
			if (mine.after(other)) return +1;
			return 0; // %%% perhaps check the rest of the range if the starts are the same?
		}

		/**
		 * Serialize the resource into XML, adding an element to the doc under the top of the stack element.
		 * 
		 * @param doc
		 *        The DOM doc to contain the XML (or null for a string return).
		 * @param stack
		 *        The DOM elements, the top of which is the containing element of the new "resource" element.
		 * @return The newly added element.
		 */
		public Element toXml(Document doc, Stack stack)
		{
			Element event = doc.createElement("event");

			if (stack.isEmpty())
			{
				doc.appendChild(event);
			}
			else
			{
				((Element) stack.peek()).appendChild(event);
			}

			stack.push(event);

			event.setAttribute("id", getId());
			event.setAttribute("range", getRange().toString());
			// add access
			event.setAttribute("access", m_access.toString());
			
			// add groups
			if ((m_groups != null) && (m_groups.size() > 0))
			{
				for (Iterator i = m_groups.iterator(); i.hasNext();)
				{
					String group = (String) i.next();
					Element sect = doc.createElement("group");
					event.appendChild(sect);
					sect.setAttribute("authzGroup", group);
				}
			}
			
			// properties
			m_properties.toXml(doc, stack);

			if ((m_attachments != null) && (m_attachments.size() > 0))
			{
				for (int i = 0; i < m_attachments.size(); i++)
				{
					Reference attch = (Reference) m_attachments.get(i);
					Element attachment = doc.createElement("attachment");
					event.appendChild(attachment);
					attachment.setAttribute("relative-url", attch.getReference());
				}
			}

			// rules
			if (m_singleRule != null)
			{
				Element rules = doc.createElement("rules");
				event.appendChild(rules);
				stack.push(rules);

				// the rule
				m_singleRule.toXml(doc, stack);

				// the exculsions
				if (m_exclusionRule != null)
				{
					m_exclusionRule.toXml(doc, stack);
				}

				stack.pop();
			}

			stack.pop();

			return event;

		} // toXml

		/**
		 * Access the event code for this edit.
		 * 
		 * @return The event code for this edit.
		 */
		protected String getEvent()
		{
			return m_event;
		}

		/**
		 * Set the event code for this edit.
		 * 
		 * @param event
		 *        The event code for this edit.
		 */
		protected void setEvent(String event)
		{
			m_event = event;
		}

		/**
		 * Access the resource's properties for modification
		 * 
		 * @return The resource's properties.
		 */
		public ResourcePropertiesEdit getPropertiesEdit()
		{
			return m_properties;

		} // getPropertiesEdit

		/**
		 * Enable editing.
		 */
		protected void activate()
		{
			m_active = true;

		} // activate

		/**
		 * Check to see if the edit is still active, or has already been closed.
		 * 
		 * @return true if the edit is active, false if it's been closed.
		 */
		public boolean isActiveEdit()
		{
			return m_active;

		} // isActiveEdit

		/**
		 * Close the edit object - it cannot be used after this.
		 */
		protected void closeEdit()
		{
			m_active = false;

		} // closeEdit

		/******************************************************************************************************************************************************************************************************************************************************
		 * AttachmentContainer implementation
		 *****************************************************************************************************************************************************************************************************************************************************/

		/**
		 * Access the attachments of the event.
		 * 
		 * @return An copy of the set of attachments (a ReferenceVector containing Reference objects) (may be empty).
		 */
		public List getAttachments()
		{
			return entityManager.newReferenceList(m_attachments);

		} // getAttachments

		/**
		 * Add an attachment.
		 * 
		 * @param ref
		 *        The attachment Reference.
		 */
		public void addAttachment(Reference ref)
		{
			m_attachments.add(ref);

		} // addAttachment

		/**
		 * Remove an attachment.
		 * 
		 * @param ref
		 *        The attachment Reference to remove (the one removed will equal this, they need not be ==).
		 */
		public void removeAttachment(Reference ref)
		{
			m_attachments.remove(ref);

		} // removeAttachment

		/**
		 * Replace the attachment set.
		 * 
		 * @param attachments
		 *        A vector of Reference objects that will become the new set of attachments.
		 */
		public void replaceAttachments(List attachments)
		{
			m_attachments.clear();

			if (attachments != null)
			{
				Iterator it = attachments.iterator();
				while (it.hasNext())
				{
					m_attachments.add(it.next());
				}
			}

		} // replaceAttachments

		/**
		 * Clear all attachments.
		 */
		public void clearAttachments()
		{
			m_attachments.clear();

		} // clearAttachments

		@Override
		public EventAccess getAccess()
		{
			return m_access;
		}
		
		@Override
		public Collection getGroups() 
		{
			return new Vector(m_groups);
		}

		@Override
		public Collection getGroupObjects()
		{
			Vector rv = new Vector();
			if (m_groups != null)
			{
				for (Iterator i = m_groups.iterator(); i.hasNext();)
				{
					String groupId = (String) i.next();
					Group group = siteService.findGroup(groupId);
					if (group != null)
					{
						rv.add(group);
					}
				}
			}

			return rv;
		}

		@Override
		public void setGroupAccess(Collection groups, boolean own) throws PermissionException
		{
			// convenience (and what else are we going to do?)
			if ((groups == null) || (groups.size() == 0))
			{
				clearGroupAccess();
				return;
			}
			
			// is there any change?  If we are already grouped, and the group list is the same, ignore the call
			if ((m_access == EventAccess.GROUPED) && (EntityCollections.isEqualEntityRefsToEntities(m_groups, groups))) return;

			// isolate any groups that would be removed or added
			Collection addedGroups = new Vector();
			Collection removedGroups = new Vector();
			EntityCollections.computeAddedRemovedEntityRefsFromNewEntitiesOldRefs(addedGroups, removedGroups, groups, m_groups);

			// verify that the user has permission to remove
			if (removedGroups.size() > 0)
			{
				// the Group objects the user has remove permission
				Collection allowedGroups = m_calendar.getGroupsAllowRemoveEvent(own);

				for (Iterator i = removedGroups.iterator(); i.hasNext();)
				{
					String ref = (String) i.next();

					// is ref a group the user can remove from?
					if ( !EntityCollections.entityCollectionContainsRefString(allowedGroups, ref) )
					{
						throw new PermissionException(sessionManager.getCurrentSessionUserId(), "access:group:remove", ref);
					}
				}
			}
			
			// verify that the user has permission to add in those contexts
			if (addedGroups.size() > 0)
			{
				// the Group objects the user has add permission
				Collection allowedGroups = m_calendar.getGroupsAllowAddEvent();

				for (Iterator i = addedGroups.iterator(); i.hasNext();)
				{
					String ref = (String) i.next();

					// is ref a group the user can remove from?
					if (!EntityCollections.entityCollectionContainsRefString(allowedGroups, ref))
					{
						throw new PermissionException(sessionManager.getCurrentSessionUserId(), "access:group:add", ref);
					}
				}
			}
			
			// we are clear to perform this
			m_access = EventAccess.GROUPED;
			EntityCollections.setEntityRefsFromEntities(m_groups, groups);
		}

		@Override
		public void clearGroupAccess() throws PermissionException
		{
			// is there any change?  If we are already channel, ignore the call
			if (m_access == EventAccess.SITE) return;

			// verify that the user has permission to add in the calendar context
			boolean allowed = m_calendar.allowAddCalendarEvent();
			if (!allowed)
			{
				throw new PermissionException(sessionManager.getCurrentSessionUserId(), "access:channel", getReference());
			}

			// we are clear to perform this
			m_access = EventAccess.SITE;
			m_groups.clear();
		}

		/******************************************************************************************************************************************************************************************************************************************************
		 * SessionBindingListener implementation
		 *****************************************************************************************************************************************************************************************************************************************************/

		public void valueBound(SessionBindingEvent event)
		{
		}

		public void valueUnbound(SessionBindingEvent event)
		{
			if (log.isDebugEnabled()) log.debug("valueUnbound()");

			// catch the case where an edit was made but never resolved
			if (m_active)
			{
				m_calendar.cancelEvent(this);
			}

		} // valueUnbound

		/**
		 * Gets the containing calendar's reference.
		 * 
		 * @return The containing calendar reference.
		 */
		public String getCalendarReference()
		{
			return m_calendar.getReference();

		} // getCalendarReference

		public String getGroupRangeForDisplay(Calendar cal) 
		{
			// TODO: check this - if used for the UI list, it needs the user's groups and the event's groups... -ggolden
			if (m_access.equals(CalendarEvent.EventAccess.SITE))
			{
				return "";
			}
			else
			{
				int count = 0;
				String allGroupString="";
				try
				{
					Site site = siteService.getSite(cal.getContext());
					for (Iterator i= m_groups.iterator(); i.hasNext();)
					{
						Group aGroup = site.getGroup((String) i.next());
						if (aGroup != null)
						{
							count++;
							if (count > 1)
							{
								allGroupString = allGroupString.concat(", ").concat(aGroup.getTitle());
							}
							else
							{
								allGroupString = aGroup.getTitle();
							}
						}
					}
				}
				catch (IdUnusedException e)
				{
					// No site available.
				}
				return allGroupString;
			}
		}

		/**
		 * Get a content handler suitable for populating this object from SAX events
		 * @return
		 */
		public ContentHandler getContentHandler(final Map<String,Object> services)
		{
			final Entity thisEntity = this;
			return new DefaultEntityHandler() {
				/* (non-Javadoc)
				 * @see org.sakaiproject.util.DefaultEntityHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
				 */
				@Override
				public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
				{
					if ( doStartElement(uri, localName, qName, attributes) ) {
						if ( "event".equals(qName) && entity == null ) {
							m_id = attributes.getValue("id");
							m_range = timeService.newTimeRange(attributes
									.getValue("range"));

							m_access = CalendarEvent.EventAccess.SITE;
							String access_str = String.valueOf(attributes
									.getValue("access"));
							if (access_str.equals(CalendarEvent.EventAccess.GROUPED
									.toString()))
								m_access = CalendarEvent.EventAccess.GROUPED;
							entity = thisEntity;
						} 
						else if ("attachment".equals(qName))
						{
							m_attachments.add(entityManager.newReference(attributes
									.getValue("relative-url")));
						}
						else if ("group".equals(qName))
						{
							m_groups.add(attributes.getValue("authzGroup"));
						}
						else if ("rules".equals(qName))
						{
							// we can ignore this as its a contianer
						}
						else if ("rule".equals(qName) || "ex-rule".equals(qName))
						{
							// get the rule name - modern style encoding
							String ruleName = StringUtils.trimToNull(attributes.getValue("name"));

							// deal with old data
							if (ruleName == null)
							{
								// get the class - this is old CHEF 1.2.10 style encoding
								String ruleClassOld = attributes.getValue("class");

								// use the last class name minus the package
								if ( ruleClassOld != null )
									ruleName = ruleClassOld.substring(ruleClassOld.lastIndexOf('.') + 1);
								if ( ruleName == null )
									log.warn("trouble loading rule");
							}

							if (ruleName != null)
							{
								// put my package on the class name
								String ruleClass = this.getClass().getPackage().getName() + "." + ruleName;

								// construct
								try
								{
									if ("rule".equals(qName))
									{
										m_singleRule = (RecurrenceRule) Class.forName(ruleClass).newInstance();
										setContentHandler(m_singleRule.getContentHandler(services), uri, localName, qName, attributes);
									}
									else // ("ex-rule".equals(qName))
									{
										m_exclusionRule = (RecurrenceRule) Class.forName(ruleClass).newInstance();
										setContentHandler(m_exclusionRule.getContentHandler(services), uri, localName, qName, attributes);
									}
								}
								catch (Exception e)
								{
									log.warn("trouble loading rule: " + ruleClass + " : " + e);
								}
							}
						}
						else 
						{
							log.warn("Unexpected Element "+qName);
						}
					} 
				}
			};

		}

	} // BaseCalendarEvent

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Storage implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	protected interface Storage
	{
		/**
		 * Open and read.
		 */
		public void open();

		/**
		 * Write and Close.
		 */
		public void close();

		/**
		 * Return the identified calendar, or null if not found.
		 */
		public Calendar getCalendar(String ref);

		/**
		 * Return true if the identified calendar exists.
		 */
		public boolean checkCalendar(String ref);

		/**
		 * Get a list of all calendars
		 */
		public List getCalendars();

		/**
		 * Keep a new calendar.
		 */
		public CalendarEdit putCalendar(String ref);

		/**
		 * Get a calendar locked for update
		 */
		public CalendarEdit editCalendar(String ref);

		/**
		 * Commit a calendar edit.
		 */
		public void commitCalendar(CalendarEdit edit);

		/**
		 * Cancel a calendar edit.
		 */
		public void cancelCalendar(CalendarEdit edit);

		/**
		 * Forget about a calendar.
		 */
		public void removeCalendar(CalendarEdit calendar);

		/**
		 * Get a event from a calendar.
		 */
		public CalendarEvent getEvent(Calendar calendar, String eventId);

		/**
		 * Get a event from a calendar locked for update
		 */
		public CalendarEventEdit editEvent(Calendar calendar, String eventId);

		/**
		 * Commit an edit.
		 */
		public void commitEvent(Calendar calendar, CalendarEventEdit edit);

		/**
		 * Cancel an edit.
		 */
		public void cancelEvent(Calendar calendar, CalendarEventEdit edit);

		/**
		 * Does this events exist in a calendar?
		 */
		public boolean checkEvent(Calendar calendar, String eventId);

		/**
		 * Get the events from a calendar, within this time range, with a limit
		 *
		 * @param calendar The calendar to query
		 * @param range The time range to query over, may be null
		 * @param limit The number of events to retrieve, may be null
		 * @return A list of CalendarEvent
		 */
		public List<CalendarEvent> getEvents(Calendar calendar, TimeRange range, Integer limit);
      
		/**
		 * Make and lock a new event.
		 */
		public CalendarEventEdit putEvent(Calendar calendar, String id);

		/**
		 * Forget about a event.
		 */
		public void removeEvent(Calendar calendar, CalendarEventEdit edit);

	} // Storage

	/**********************************************************************************************************************************************************************************************************************************************************
	 * StorageUser implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Construct a new continer given just an id.
	 * 
	 * @param ref
	 *        The reference for the new object.
	 * @return The new containe Resource.
	 */
	public Entity newContainer(String ref)
	{
		return new BaseCalendarEdit(ref);
	}

	/**
	 * Construct a new container resource, from an XML element.
	 * 
	 * @param element
	 *        The XML.
	 * @return The new container resource.
	 */
	public Entity newContainer(Element element)
	{
		return new BaseCalendarEdit(element);
	}

	/**
	 * Construct a new container resource, as a copy of another
	 * 
	 * @param other
	 *        The other contianer to copy.
	 * @return The new container resource.
	 */
	public Entity newContainer(Entity other)
	{
		return new BaseCalendarEdit((Calendar) other);
	}

	/**
	 * Construct a new rsource given just an id.
	 * 
	 * @param container
	 *        The Resource that is the container for the new resource (may be null).
	 * @param id
	 *        The id for the new object.
	 * @param others
	 *        (options) array of objects to load into the Resource's fields.
	 * @return The new resource.
	 */
	public Entity newResource(Entity container, String id, Object[] others)
	{
		return new BaseCalendarEventEdit((Calendar) container, id);
	}

	/**
	 * Construct a new resource, from an XML element.
	 * 
	 * @param container
	 *        The Resource that is the container for the new resource (may be null).
	 * @param element
	 *        The XML.
	 * @return The new resource from the XML.
	 */
	public Entity newResource(Entity container, Element element)
	{
		return new BaseCalendarEventEdit((Calendar) container, element);
	}

	/**
	 * Construct a new resource from another resource of the same type.
	 * 
	 * @param container
	 *        The Resource that is the container for the new resource (may be null).
	 * @param other
	 *        The other resource.
	 * @return The new resource as a copy of the other.
	 */
	public Entity newResource(Entity container, Entity other)
	{
		return new BaseCalendarEventEdit((Calendar) container, (CalendarEvent) other);
	}

	/**
	 * Construct a new continer given just an id.
	 * 
	 * @param ref
	 *        The reference for the new object.
	 * @return The new containe Resource.
	 */
	public Edit newContainerEdit(String ref)
	{
		BaseCalendarEdit rv = new BaseCalendarEdit(ref);
		rv.activate();
		return rv;
	}

	/**
	 * Construct a new container resource, from an XML element.
	 * 
	 * @param element
	 *        The XML.
	 * @return The new container resource.
	 */
	public Edit newContainerEdit(Element element)
	{
		BaseCalendarEdit rv = new BaseCalendarEdit(element);
		rv.activate();
		return rv;
	}

	/**
	 * Construct a new container resource, as a copy of another
	 * 
	 * @param other
	 *        The other contianer to copy.
	 * @return The new container resource.
	 */
	public Edit newContainerEdit(Entity other)
	{
		BaseCalendarEdit rv = new BaseCalendarEdit((Calendar) other);
		rv.activate();
		return rv;
	}

	/**
	 * Construct a new rsource given just an id.
	 * 
	 * @param container
	 *        The Resource that is the container for the new resource (may be null).
	 * @param id
	 *        The id for the new object.
	 * @param others
	 *        (options) array of objects to load into the Resource's fields.
	 * @return The new resource.
	 */
	public Edit newResourceEdit(Entity container, String id, Object[] others)
	{
		BaseCalendarEventEdit rv = new BaseCalendarEventEdit((Calendar) container, id);
		rv.activate();
		return rv;
	}

	/**
	 * Construct a new resource, from an XML element.
	 * 
	 * @param container
	 *        The Resource that is the container for the new resource (may be null).
	 * @param element
	 *        The XML.
	 * @return The new resource from the XML.
	 */
	public Edit newResourceEdit(Entity container, Element element)
	{
		BaseCalendarEventEdit rv = new BaseCalendarEventEdit((Calendar) container, element);
		rv.activate();
		return rv;
	}

	/**
	 * Construct a new resource from another resource of the same type.
	 * 
	 * @param container
	 *        The Resource that is the container for the new resource (may be null).
	 * @param other
	 *        The other resource.
	 * @return The new resource as a copy of the other.
	 */
	public Edit newResourceEdit(Entity container, Entity other)
	{
		BaseCalendarEventEdit rv = new BaseCalendarEventEdit((Calendar) container, (CalendarEvent) other);
		rv.activate();
		return rv;
	}

	/**
	 * Collect the fields that need to be stored outside the XML (for the resource).
	 * 
	 * @return An array of field values to store in the record outside the XML (for the resource).
	 */
	public Object[] storageFields(Entity r)
	{
		Object[] rv = new Object[4];
		TimeRange range = ((CalendarEvent) r).getRange();
		rv[0] = range.firstTime(); // %%% fudge?
		rv[1] = range.lastTime(); // %%% fudge?
		
		// we use hours rather than ms for the range to reduce the index size in the database
		// I dont what to use days just incase we want sub day range finds
		long oneHour = 60L*60L*1000L;
		rv[2] = (int)(range.firstTime().getTime()/oneHour);
		rv[3] = (int)(range.lastTime().getTime()/oneHour);

		// find the end of the sequence
		RecurrenceRuleBase rr = (RecurrenceRuleBase)((CalendarEvent) r).getRecurrenceRule();
		if ( rr != null ) {
			Time until = rr.getUntil();
			if ( until != null ) {
				rv[3] = (int)(until.getTime()/oneHour);
			} else {
				int count = rr.getCount();
				int interval = rr.getInterval();
				long endevent = range.lastTime().getTime();
				if ( count == 0 ) {
					rv[3] = Integer.MAX_VALUE-1; // hours since epoch, this represnts 9 Oct 246953 07:00:00
 				} else {
					String frequency = rr.getFrequency();
					GregorianCalendar c = new GregorianCalendar();
					c.setTimeInMillis(endevent);
					c.add(rr.getRecurrenceType(), count*interval);
					rv[3] = (int)(c.getTimeInMillis()/oneHour);
				}
			}
		}
		return rv;
	}

	/**
	 * Check if this resource is in draft mode.
	 * 
	 * @param r
	 *        The resource.
	 * @return true if the resource is in draft mode, false if not.
	 */
	public boolean isDraft(Entity r)
	{
		return false;
	}

	/**
	 * Access the resource owner user id.
	 * 
	 * @param r
	 *        The resource.
	 * @return The resource owner user id.
	 */
	public String getOwnerId(Entity r)
	{
		return null;
	}

	/**
	 * Access the resource date.
	 * 
	 * @param r
	 *        The resource.
	 * @return The resource date.
	 */
	public Time getDate(Entity r)
	{
		return null;
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * PDF file generation
	 *********************************************************************************************************************************************************************************************************************************************************/

	// Mime Types
	protected final static String PDF_MIME_TYPE = "application/pdf";
	protected final static String ICAL_MIME_TYPE = "text/calendar";

	// URL Parameter Constants
	protected static final String TIME_RANGE_PARAMETER_NAME = "timeRange";
	protected final static String USER_NAME_PARAMETER_NAME = "user";
	protected final static String CALENDAR_PARAMETER_BASE_NAME = "calendar";
	protected final static String SCHEDULE_TYPE_PARAMETER_NAME = "scheduleType";
	protected final static String SELECTED_CALENDAR_DATE_PARAMETER_NAME = "selectedCalendarDate";	
	protected final static String ORDER_EVENTS_PARAMETER_NAME = "order";

	/**
	 * Debugging routine to get a string for a TimeRange. This should probably be in the TimeRange class.
	 */
	protected String dumpTimeRange(TimeRange timeRange)
	{
		String returnString = "";

		if (timeRange != null)
		{
			returnString = timeRange.firstTime().toStringLocalFull() + " - " + timeRange.lastTime().toStringLocalFull();
		}

		return returnString;
	}

	/**
	 * @param ical
	 *        iCal object
	 * @param calRefs
	 *        This is the name of the user whose schedule is being printed.
	 * @return Number of events generated in ical object
	 */
	protected int generateICal(net.fortuna.ical4j.model.Calendar ical, List<String> calRefs)
	{
		int numEvents = 0;
		
		// This list will have an entry for every week day that we care about.
		TimeRange currentTimeRange = getICalTimeRange();

		// Get a list of events.
		CalendarEventVector calendarEventVector = getEvents(calRefs, currentTimeRange);
		Iterator itEvent = calendarEventVector.iterator();

		// Generate XML for all the events.
		while (itEvent.hasNext())
		{
			CalendarEvent event = (CalendarEvent) itEvent.next();

			DateTime icalStartDate = new DateTime(event.getRange().firstTime().getTime());
			
			long seconds = event.getRange().duration() / 1000;
			VEvent icalEvent = new VEvent(icalStartDate, Duration.ofSeconds(seconds), event.getDisplayName() );
			
			net.fortuna.ical4j.model.parameter.TzId tzId = new net.fortuna.ical4j.model.parameter.TzId( timeService.getLocalTimeZone().getID() );
			icalEvent.getProperty(Property.DTSTART).getParameters().add(tzId);
			icalEvent.getProperty(Property.DTSTART).getParameters().add(Value.DATE_TIME);
			icalEvent.getProperties().add(new Uid(event.getId()));
			// build the description, adding links to attachments if necessary
			StringBuffer description = new StringBuffer("");
			if ( event.getDescription() != null && !event.getDescription().equals("") )
				description.append(event.getDescription());
			
			List attachments = event.getAttachments();
			if(attachments != null){
				for (Iterator iter = attachments.iterator(); iter.hasNext();) {
					Reference attachment = (Reference) iter.next();
					description.append("\n");
					description.append(attachment.getUrl());
					description.append("\n");
				}
			}
			if(description.length() > 0) {
				//Replace \r with \n
				icalEvent.getProperties().add(new Description(description.toString().replace('\r', '\n')));
			}

			if ( event.getLocation() != null && !event.getLocation().equals("") ) {
				icalEvent.getProperties().add(new Location(event.getLocation().replace('\r', '\n')));
			}

			try
			{
				String organizer = userDirectoryService.getUser( event.getCreator() ).getDisplayName();
				organizer = organizer.replaceAll(" ","%20"); // get rid of illegal URI characters
				icalEvent.getProperties().add(new Organizer(new URI("CN="+organizer)));
			}
			catch (UserNotDefinedException e) {} // ignore
			catch (URISyntaxException e) {} // ignore
         
			StringBuffer comment = new StringBuffer(event.getType());
			comment.append(" (");
			comment.append(event.getSiteName());
			comment.append(")");
			icalEvent.getProperties().add(new Comment(comment.toString()));
			
			ical.getComponents().add( icalEvent );
			numEvents++;
			
			/* TBD: add to VEvent: recurring schedule, ...
			RecurenceRUle x = event.getRecurrenceRule();
			*/
		}
		
		return numEvents;
	}
	
	/* Given a current date via the calendarUtil paramter, returns a TimeRange for the year,
	  *fromMonthsInput number of months from past to be included
+	  *toMonthsInput number of months in future  to be included.
	 */
	public TimeRange getICalTimeRange()
	{
		int toMonthsInput = serverConfigurationService.getInt("calendar.export.next.months",12);
		int fromMonthsInput = serverConfigurationService.getInt("calendar.export.previous.months",6);

		java.util.Calendar calTo = java.util.Calendar.getInstance();
		calTo.add(java.util.Calendar.MONTH, toMonthsInput);

		java.util.Calendar calFrom = java.util.Calendar.getInstance();
		calFrom.add(java.util.Calendar.MONTH, -fromMonthsInput);

		Time startTime = timeService.newTime(calFrom.getTimeInMillis());
		Time endTime = timeService.newTime(calTo.getTimeInMillis());
		
		return timeService.newTimeRange(startTime,endTime,true,true);
	}

	/**
	 * Gets the schedule type from a Properties object (filled from a URL parameter list).
	 */
	protected int getScheduleTypeFromParameterList(Properties parameters) {
		int scheduleType = UNKNOWN_VIEW;

		// Get the type of schedule (daily, weekly, etc.)
		String scheduleTypeString = (String) parameters.get(SCHEDULE_TYPE_PARAMETER_NAME);
		scheduleType = Integer.parseInt(scheduleTypeString);

		return scheduleType;
	}

	/**
	 * Gets the schedule type from a Properties object (filled from a URL parameter list).
	 */
	protected Instant getSelectedCalendarDateFromParameterList(Properties parameters) {
		// Get the selected calendar date in ISO format
		// Example 2021-08-06T08:22:27.789Z
		String selectedCalendarDate = (String) parameters.get(SELECTED_CALENDAR_DATE_PARAMETER_NAME);
		if (StringUtils.isBlank(selectedCalendarDate)) {
			return null;
		}		
		Instant selectedCalendarInstant = Instant.parse(selectedCalendarDate);
		return selectedCalendarInstant;
	}

	/**
	 * Access some named configuration value as a string.
	 * 
	 * @param name
	 *        The configuration value name.
	 * @param dflt
	 *        The value to return if not found.
	 * @return The configuration value with this name, or the default value if not found.
	 */
	protected String getString(String name, String dflt)
	{
		return serverConfigurationService.getString(name, dflt);
	}

	/*
	 * Gets the time range parameter from a Properties object filled from URL parameters.
	 */
	protected TimeRange getTimeRangeFromParameters(Properties parameters)
	{
		return getTimeRangeParameterByName(parameters, TIME_RANGE_PARAMETER_NAME);
	}

	/**
	 * Utility routine to get a time range parameter from the URL parameters store in a Properties object.
	 */
	protected TimeRange getTimeRangeParameterByName(Properties parameters, String name)
	{
		// Now get the time range.
		String timeRangeString = (String) parameters.get(name);

		TimeRange timeRange = null;
		timeRange = timeService.newTimeRange(timeRangeString);

		return timeRange;
	}




	protected List getCalendarReferenceList()
	throws PermissionException
	{
		// Get the list of calendars.from user session
		List calendarReferenceList = (List)sessionManager.getCurrentSession().getAttribute(SESSION_CALENDAR_LIST);
	
		// check if there is any calendar to which the user has acces
		Iterator it = calendarReferenceList.iterator();
		int permissionCount = calendarReferenceList.size();
		while (it.hasNext())
		{
			String calendarReference = (String) it.next();
			try
			{
				getCalendar(calendarReference);
			}
	
			catch (IdUnusedException e)
			{
				continue;
			}
	
			catch (PermissionException e)
			{
				permissionCount--;
				continue;
			}
		}
		// if no permission to any of the calendars, throw exception and do nothing
		// the expection will be caught by AccessServlet.doPrintingRequest()
		if (permissionCount == 0)
		{
			throw new PermissionException("", "", "");
		}
		
		return calendarReferenceList;
	}

	protected void printICalSchedule(String calendarName, List<String> calRefs, OutputStream os)
//	protected void printICalSchedule(String calRef, OutputStream os) 
		throws PermissionException
	{
		// generate iCal text file 
		net.fortuna.ical4j.model.Calendar ical = new net.fortuna.ical4j.model.Calendar();
		ical.getProperties().add(new ProdId("-//SakaiProject//iCal4j 1.0//EN"));
		ical.getProperties().add(Version.VERSION_2_0);
		ical.getProperties().add(CalScale.GREGORIAN);
		ical.getProperties().add(new XProperty("X-WR-CALNAME", calendarName));
		ical.getProperties().add(new XProperty("X-WR-CALDESC", calendarName));
		
		TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry(); 
		TzId tzId = new TzId( timeService.getLocalTimeZone().getID() );
		ical.getComponents().add(registry.getTimeZone(tzId.getValue()).getVTimeZone());
		
		CalendarOutputter icalOut = new CalendarOutputter();
		int numEvents = generateICal(ical, calRefs);
			
		try 
		{
			if (numEvents > 0) {
				icalOut.output( ical, os );
			}
		}
		catch (Exception e)
		{
           log.warn(".printICalSchedule(): ", e);
		}
	}

	/**
	 * Called by the servlet to service a get/post requesting a calendar in PDF format.
	 */
	protected void printSchedule(Properties parameters, OutputStream os) throws PermissionException {
		// Get the user name.
		String userName = (String) parameters.get(USER_NAME_PARAMETER_NAME);

		// Get the list of calendars.from user session
		List calendarReferenceList = getCalendarReferenceList();

		// Get the type of schedule (daily, weekly, etc.)
		int scheduleType = getScheduleTypeFromParameterList(parameters);

		// Get the selected calendar instant
		Instant selectedCalendarInstant = this.getSelectedCalendarDateFromParameterList(parameters);

		// Now get the time range, the time range is only used by the OLD VIEW list, none of the new full calendar views use it.
		TimeRange timeRange = getTimeRangeFromParameters(parameters);

		// Now get the order
		boolean reverseOrder = Boolean.parseBoolean( (String) parameters.get(ORDER_EVENTS_PARAMETER_NAME) );

		Document document = docBuilder.newDocument();

		pdfExportService.generateXMLDocument(scheduleType, document, timeRange, selectedCalendarInstant, calendarReferenceList, userName, this, reverseOrder);
		pdfExportService.generatePDF(document, pdfExportService.getXSLFileNameForScheduleType(scheduleType), os);
	}

   /**
	 * Get a DefaultHandler so that the StorageUser here can parse using SAX events.
	 * 
	 * @see org.sakaiproject.util.SAXEntityReader#getDefaultHandler(Map<String,Object>)
	 */
	public DefaultEntityHandler getDefaultHandler(final Map<String,Object> services)
	{
		return new DefaultEntityHandler()
		{


			/*
			 * (non-Javadoc)
			 * 
			 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
			 *      java.lang.String, java.lang.String, org.xml.sax.Attributes)
			 */
			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) throws SAXException
			{
				if (doStartElement(uri, localName, qName, attributes))
				{
					if (entity == null)
					{
						if ("calendar".equals(qName))
						{
							BaseCalendarEdit bce = new BaseCalendarEdit();
							entity = bce;
							setContentHandler(bce.getContentHandler(services), uri, localName,
									qName, attributes);
						}
						else if ("event".equals(qName))
						{
							BaseCalendarEventEdit bcee = new BaseCalendarEventEdit(
									container);
							entity = bcee;
							setContentHandler(bcee.getContentHandler(services), uri, localName,
									qName, attributes);

						} else {
							log.warn("Unexpected Element in XML ["+qName+"]");
						}

					}
				}
			}

		};
	}

	/* (non-Javadoc)
	 * @see org.sakaiproject.util.SAXEntityReader#getServices()
	 */
	public Map<String, Object> getServices()
	{
		if ( m_services == null ) {
			m_services = new HashMap<String, Object>();
			m_services.put("timeservice", timeService);
		}
		return m_services;
	}
	public void setServices(Map<String,Object> services) {
		m_services = services;
	}

	public Map<String, String> transferCopyEntities(String fromContext, String toContext, List<String> ids, List<String> options, boolean cleanup)
	{
		Map<String, String> transversalMap = new HashMap<String, String>();
		try
		{
			if(cleanup == true)
			{
				String toSiteId = toContext;	
				String calendarId = calendarReference(toSiteId, SiteService.MAIN_CONTAINER);
				Calendar calendarObj = getCalendar(calendarId);	
				List calEvents = calendarObj.getEvents(null,null);
				
				for (int i = 0; i < calEvents.size(); i++)
				{
					try
					{	
						CalendarEvent ce = (CalendarEvent) calEvents.get(i);	
						calendarObj.removeEvent(calendarObj.getEditEvent(ce.getId(), CalendarService.EVENT_REMOVE_CALENDAR));
						CalendarEventEdit edit = calendarObj.getEditEvent(ce.getId(), org.sakaiproject.calendar.api.CalendarService.EVENT_REMOVE_CALENDAR);
						calendarObj.removeEvent(edit);
						calendarObj.commitEvent(edit);	
					}
					catch (IdUnusedException e)
					{
						log.debug(".IdUnusedException " + e);
					}
					catch (PermissionException e)
					{
						log.debug(".PermissionException " + e);
					}
					catch (InUseException e)
					{
						log.debug(".InUseException delete" + e);
					}
				}
				
			}
			transversalMap.putAll(transferCopyEntities(fromContext, toContext, ids, null));
		}
		catch (Exception e)
		{
			log.info("importSiteClean: End removing Calendar data" + e);
		}
		
		return transversalMap;
	}

	/** 
	 ** Comparator for sorting Group objects
	 **/
	private class GroupComparator implements Comparator {
		public int compare(Object o1, Object o2) {
			return ((Group)o1).getTitle().compareToIgnoreCase( ((Group)o2).getTitle() );
		}
	}
	
	public String calendarOpaqueUrlReference(Reference ref)
	{
		// TODO: Currently not sure whether alias handling will be required for this or not.
		OpaqueUrl opaqUrl = opaqueUrlDao.getOpaqueUrl(sessionManager.getCurrentSessionUserId(), ref.getReference());
		return getAccessPoint(true) + Entity.SEPARATOR + REF_TYPE_CALENDAR_OPAQUEURL + Entity.SEPARATOR + opaqUrl.getOpaqueUUID() + Entity.SEPARATOR + ref.getId() + ICAL_EXTENSION;
	}
	
	/**
 	/**
	 * check permissions for subscribing to the implicit calendar.
	 * 
	 * @param ref
	 *        The calendar reference.
	 * @return true if the user is allowed to subscribe to the implicit calendar, false if not.
	 */
	public boolean allowSubscribeThisCalendar(String ref)
	{
		// If you can read this calendar, you may subscribe to it:
		return unlockCheck(AUTH_READ_CALENDAR, ref);
	}
	
	protected String mapOpaqueGuidToContextId(Reference reference, String opaqueGuid)
	{
		OpaqueUrl opaqUrl = opaqueUrlDao.getOpaqueUrl(opaqueGuid);
		if (opaqUrl != null)
		{
			String[] parts = StringUtils.split(opaqUrl.getCalendarRef(), Entity.SEPARATOR);
			//This was originally parts[3], neither seem to work
			return parts[2];
		}
		
		return null;
	}
	
	protected String extractOpaqueGuid(Reference reference) throws EntityNotDefinedException
	{
		// subType at [2], opaqueGuid [3]:
		String[] parts = StringUtils.split(reference.getReference(), Entity.SEPARATOR);
		if (parts.length < 4 || !REF_TYPE_CALENDAR_OPAQUEURL.equals(parts[1]))
		{
			throw new EntityNotDefinedException(reference.getReference());
		}
		return parts[2];
	}
	
	protected void handleAccessPdf(HttpServletRequest req, HttpServletResponse res, Reference ref, String calRef)
			throws EntityPermissionException, EntityNotDefinedException {
		try
		{
			Properties options = new Properties();
			Enumeration e = req.getParameterNames();
			while (e.hasMoreElements())
			{
				String key = (String) e.nextElement();
				String[] values = req.getParameterValues(key);
				if (values.length == 1)
				{
					options.put(key, values[0]);
				}
				else
				{
					StringBuilder buf = new StringBuilder();
					for (int i = 0; i < values.length; i++)
					{
						buf.append(values[i] + "^");
					}
					options.put(key, buf.toString());
				}
			}
			
			// We need to write to a temporary stream for better speed, plus
			// so we can get a byte count. Internet Explorer has problems
			// if we don't make the setContentLength() call.
			ByteArrayOutputStream outByteStream = new ByteArrayOutputStream();
			res.addHeader("Content-Disposition", "inline; filename=\"schedule.pdf\"");
			res.setContentType(PDF_MIME_TYPE);
			printSchedule(options, outByteStream);
			res.setContentLength(outByteStream.size());
			
			if (outByteStream.size() > 0)
			{
				// Increase the buffer size for more speed.
				res.setBufferSize(outByteStream.size());
			}
			
			OutputStream out = null;
			try
			{
				out = res.getOutputStream();
				if (outByteStream.size() > 0)
				{
					outByteStream.writeTo(out);
				}
				out.flush();
				out.close();
			}
			catch (Throwable ignore)
			{
			}
			finally
			{
				if (out != null)
				{
					try
					{
						out.close();
					}
					catch (Throwable ignore)
					{
					}
				}
			}
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Throwable t)
		{
			throw new EntityNotDefinedException(ref.getReference());
		}
	}
	
	protected void handleAccessIcalCommon(HttpServletRequest req, HttpServletResponse res, Reference ref, String calRef)
			throws EntityPermissionException, PermissionException, IOException {

		// Ok so we need to check to see if we've handled this reference before.
		// This is to prevent loops when including calendars
		// that currently includes other calendars we only do the check in here.
		if (getUserAgent().equals(req.getHeader("User-Agent"))) {
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			log.warn("Reject internal request for: "+ calRef);
			return;
		}

		// Extract the alias name to use for the filename.
		List<Alias> alias =  aliasService.getAliases(calRef);
		String aliasName = "schedule.ics";
		if ( ! alias.isEmpty() )
			aliasName =  alias.get(0).getId();
		
		List<String> referenceList = getCalendarReferences(ref.getContext());
		Time modDate = timeService.newTime(0);

		// update date/time reference
		for (String curCalRef: referenceList)
		{
			Calendar curCal = findCalendar(curCalRef);
			/*
			 * TODO: This null check is required to handle the references 
			 * pertaining to external calendar subscriptions as they are 
			 * currently broken in (at least) the 2 following ways:
			 * 
			 * (i) findCalendar will return null rather than a calendar object.
			 * (ii) getCalendar(String) will return a calendar object that is 
			 * not null, but the corresponding getModified() method returns a
			 * date than can not be parsed.  
			 *  
			 * Clearly such references to need to be improved to make them 
			 * consistent with other types as at the moment they have to be
			 * excluded as part of this process to find the most recent modified
			 * date. 
			 */
			if (curCal == null)
			{	
				continue;
			}
			Time curModDate = curCal.getModified();
			if ( curModDate != null && curModDate.after(modDate))
			{
				modDate = curModDate;
			}
		}
		res.addHeader("Content-Disposition", "inline; filename=\"" + aliasName + "\"");
		res.setContentType(ICAL_MIME_TYPE);
		res.setDateHeader("Last-Modified", modDate.getTime() );
		String calendarName = "";
		try {
			calendarName = siteService.getSite(ref.getContext()).getTitle();
			boolean isMyDashboard = siteService.isUserSite(ref.getContext());
			if (isMyDashboard){
				calendarName = serverConfigurationService.getString(UI_SERVICE, SAKAI);
			}
		} catch (IdUnusedException e) {
		}
		printICalSchedule(calendarName, referenceList, res.getOutputStream());
	}
	
	protected void handleAccessIcal(HttpServletRequest req, HttpServletResponse res, Reference ref, String calRef)
			throws EntityPermissionException, EntityNotDefinedException {
		
		// check if ical export is enabled
		if (!getExportEnabled(calRef))
		{
			throw new EntityNotDefinedException(ref.getReference());
		}
		
		// Make sure the current user can access this calendar first.
		if (siteService.isUserSite(ref.getContext()) && !allowGetCalendar(calRef))
		{
			throw new EntityPermissionException(sessionManager.getCurrentSessionUserId(), SECURE_READ, calRef);
		}
		
		try
		{
			handleAccessIcalCommon(req, res, ref, calRef);
			
			OutputStream out = null;
			try
			{
				out = res.getOutputStream();
				out.flush();
				out.close();
			}
			catch (Throwable ignore)
			{
			}
			finally
			{
				if (out != null)
				{
					try
					{
						out.close();
					}
					catch (Throwable ignore)
					{
					}
				}
			}
		}
		catch (EntityPermissionException epe)
		{
			throw epe;
		}
		catch (Throwable t)
		{
			throw new EntityNotDefinedException(ref.getReference());
		}
	}
	
	protected void handleAccessOpaqueUrl(HttpServletRequest req, HttpServletResponse res, Reference ref, String calRef)
			throws EntityPermissionException, EntityNotDefinedException {
		
		// Get the user UUID from any opaque GUID within the reference:
		String opaqueGuid = extractOpaqueGuid(ref);
		String userId = null;
		if (opaqueGuid != null)
		{
			OpaqueUrl opaqUrl = opaqueUrlDao.getOpaqueUrl(opaqueGuid);
			userId = (opaqUrl != null) ? opaqUrl.getUserUUID() : null;
		}
		if (opaqueGuid == null || userId == null)
		{
			throw new EntityNotDefinedException(ref.getReference());
		}
		
		boolean isAlreadyLoggedIn = false;
		
		try 
		{
			// We want to avoid an inadvertent logout coming from the same UA:
			UsageSession usage = usageSessionService.getSession();
			if ((usage != null) && userId.equals(usage.getUserId()) && !usage.isClosed())
			{
				isAlreadyLoggedIn = true;
			}
			String eid = userDirectoryService.getUserEid(userId);
			Authentication authn = new Authentication(userId, eid);
			if (usageSessionService.login(authn, req))
			{
				// Make sure the current user can access this calendar first.
				if (allowGetCalendar(calRef)) 
				{
					handleAccessIcalCommon(req, res, ref, calRef);
				}
				else
				{
					log.warn("Calendar access via opaque UUID failed: " + opaqueGuid);
					throw new EntityNotDefinedException(opaqueGuid);
				}
			}
		} 
		catch (UserNotDefinedException e) 
		{
			log.warn("User not found: " + userId);
			throw new EntityNotDefinedException(ref.getReference());
		} 
		catch (PermissionException e) 
		{
			log.warn("Calendar access via opaque UUID failed: " + opaqueGuid);
			throw new EntityNotDefinedException(opaqueGuid);
		} 
		catch (IOException e) 
		{
		}
		finally
		{
			if (!isAlreadyLoggedIn)
			{
				usageSessionService.logout();
			}
		}
	}

	/**
	 * JavaDoc can be found org.sakaiproject.calendar.api.CalendarService.
	 * @param siteId
	 * @return
	 */
	public List<String> getCalendarReferences(String siteId) {
		// get merged calendars channel refs
		String initMergeList = null;
		try {
			ToolConfiguration tc = siteService.getSite(siteId).getToolForCommonId("sakai.schedule");
			if (tc != null) {
				initMergeList = tc.getPlacementConfig().getProperty("mergedCalendarReferences");
			}
		} catch (IdUnusedException e){
			initMergeList = null;
		}
		
		// load all calendar channels (either primary or merged calendars)
		String primaryCalendarReference = calendarReference(siteId, SiteService.MAIN_CONTAINER);
		MergedList mergedCalendarList = loadChannels(siteId, primaryCalendarReference, initMergeList, null);
		
		// add external calendar subscriptions
		List referenceList = mergedCalendarList.getReferenceList();
		Set<ExternalSubscriptionDetails> subscriptionDetailsList = externalCalendarSubscriptionService.getCalendarSubscriptionChannelsForChannels(primaryCalendarReference,referenceList);
        subscriptionDetailsList.stream().forEach(x->referenceList.add(x.getReference()));
		
		return referenceList;
	}
	
	/**
	 ** loadChannels -- load specified primaryCalendarReference or merged
	 ** calendars if initMergeList is defined
	 **/
	protected MergedList loadChannels(String siteId, String primaryCalendarReference, String initMergeList, MergedList.EntryProvider entryProvider) {
		MergedList mergedCalendarList = new MergedList();
		String[] channelArray = null;
		boolean isOnWorkspaceTab = siteService.isUserSite(siteId);
		
		// Figure out the list of channel references that we'll be using.
		// MyWorkspace is special: if not superuser, and not otherwise defined,
		// get all channels
		if (isOnWorkspaceTab && !securityService.isSuperUser() && initMergeList == null) {
			channelArray = mergedCalendarList.getAllPermittedChannels(new CalendarChannelReferenceMaker());
		} else {
			channelArray = mergedCalendarList.getChannelReferenceArrayFromDelimitedString(primaryCalendarReference, initMergeList);
		}
		if (entryProvider == null) {
			entryProvider = new MergedListEntryProviderFixedListWrapper(new EntryProvider(), primaryCalendarReference, channelArray, new CalendarReferenceToChannelConverter());
		}
		mergedCalendarList.loadChannelsFromDelimitedString(isOnWorkspaceTab, false, entryProvider, sessionManager.getCurrentSessionUserId(), channelArray, securityService.isSuperUser(), siteId);
		
		return mergedCalendarList;
	}
	

	/**
	 * Get the user agent we should use for request to get other calendars.
	 * @return The user agent.
	 */
	String getUserAgent() {
		return "Sakai/"+ serverConfigurationService.getString("version.sakai", "?") + " (Calendar Subscription)";
	}
	
	// Returns the calendar tool id string.
	public String getToolId(){
		return "sakai.schedule";		
	}

	// Checks the calendar has been created. For now just returning true to support the API contract.
	public boolean isCalendarToolInitialized(String siteId){
		return true;
	}

	// Private helper method to generate a time range one year before and one year after the current time
	private TimeRange getOneYearTimeRange() {
		Instant now = Instant.now();

		// Create a time range from one year ago to one year from now
		Instant oneYearAgo = now.minus(365, ChronoUnit.DAYS);
		Instant oneYearLater = now.plus(365, ChronoUnit.DAYS);

		return timeService.newTimeRange(oneYearAgo, oneYearLater);
	}

	private String getDirectToolUrl(String siteId) throws IdUnusedException {
		ToolConfiguration toolConfig = siteService.getSite(siteId).getToolForCommonId("sakai.schedule");
		return serverConfigurationService.getPortalUrl() + "/directtool/" + toolConfig.getId();
	}
}

