package hu.sztaki.lpds.dataavenue.core;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.NamedQuery;
import javax.persistence.Transient;

@NamedQuery(
	    name="Ticket.findAll",
	    query="SELECT i FROM Ticket AS i ORDER BY i.created DESC"
)

@Entity
/*@Table(uniqueConstraints=@UniqueConstraint(columnNames="email")) -- same email can be used in different portals, so won't be unique */
public class Ticket {
	
	static final int STRING_SQL_SIZE = 255;
	
	@Transient  
	final AtomicInteger activeSessions = new AtomicInteger(2);
	
	public Ticket() {}

	@Id
	private String ticket = UUID.randomUUID().toString();
	public String getTicket() { return ticket; }
	public void setTicket(String ticket) { this.ticket = ticket; }
	
	@Column(nullable=false)
	private String name = null;
	public String getName() { return name; }
	public void setName(String name) { this.name = DBManager.abbreviate(name); }
	
	@Column(nullable=false)
	private String email = null;
	public String getEmail() { return email; }
	public void setEmail(String email) {
		if (email == null || email.length() > DBManager.SQL_DEFAULT_STRING_LENGTH) throw new RuntimeException("No email provided or its size exceeds " + DBManager.SQL_DEFAULT_STRING_LENGTH);
		this.email = email; 
	}

	private String company = null;
	public String getCompany() { return company; }
	public void setCompany(String company) { this.company = DBManager.abbreviate(company); }
	
	public enum TicketTypesEnum { ADMIN, PORTAL_ADMIN, PORTAL_USER, API_USER };
	private TicketTypesEnum ticketType = TicketTypesEnum.API_USER;
	public TicketTypesEnum getTicketType() { return ticketType; }
	public void setTicketType(TicketTypesEnum ticketType) { this.ticketType = ticketType; }

	private long created = System.currentTimeMillis(); // time of creation
	public void setCreated(long created) { this.created = created; } 
	public long getCreated() { return created; }
	public String getCreatedString() { return Utils.dateString(created); }
	
	private Long validThru = null; // never expires
	public void setValidThru(Long validThru) { this.validThru = validThru; } 
	public long getValidThru() { return validThru != null ? validThru : 0l; }
	public String getValidThruString() { return validThru == null || validThru == 0l ? "-" : Utils.dateString(validThru); }

	private boolean isDisabled = false; // tickets can temporarily be disabled
	public boolean isDisabled() { return isDisabled; }
	public void setDisabled(boolean isDisabled) { this.isDisabled = isDisabled; }

	private String parent = null; // if this ticket is generated by an admin ticket with grant
	public String getParent() { return parent; }
	public void setParent(String parent) { this.parent = parent; }
	
	private Integer maxSessions = null; // not limited; maximum number of simutaneous connections (e.g. users/ticket)
	public int getMaxSessions() { return maxSessions != null ? maxSessions : 0; }
	public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions;} // FIXME null => 0
	
	private Integer maxTransfers = null; // not limited; maximum number of simultaneous copy/move operations
	public int getMaxTransfers() { return maxTransfers != null ? maxTransfers : 0; }
	public void setMaxTransfers(int maxTransfers) { this.maxTransfers = maxTransfers; }

	private Integer maxAliases = null; // not limited; maximum number of (live) aliases that a user can created
	public int getMaxAliases() { return maxAliases != null ? maxAliases : 0; }
	public void setMaxAliases(int maxAliases) { this.maxAliases = maxAliases; }
	
	private Float latitude, longitude, accuracy; // optional GPS coordinates
	public Float getLatitude() { return latitude; }
	public void setLatitude(Float latitude) { this.latitude = latitude; }
	public Float getLongitude() { return longitude; }
	public void setLongitude(Float longitude) { this.longitude = longitude; }
	public Float getAccuracy() { return accuracy; }
	public void setAccuracy(Float accuracy) { this.accuracy = accuracy; }
	
	@Lob
	private String comments;
	public String getComments() { return comments; }
	public void setComments(String comments) { this.comments = comments; }
}