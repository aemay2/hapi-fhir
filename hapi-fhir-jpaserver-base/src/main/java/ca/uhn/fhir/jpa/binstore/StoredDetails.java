package ca.uhn.fhir.jpa.binstore;

import ca.uhn.fhir.jpa.util.JsonDateDeserializer;
import ca.uhn.fhir.jpa.util.JsonDateSerializer;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.hash.HashingInputStream;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.annotation.Nonnull;
import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonAutoDetect(creatorVisibility = JsonAutoDetect.Visibility.NONE, fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public class StoredDetails {

	@JsonProperty("blobId")
	private String myBlobId;
	@JsonProperty("bytes")
	private long myBytes;
	@JsonProperty("contentType")
	private String myContentType;
	@JsonProperty("hash")
	private String myHash;
	@JsonProperty("published")
	@JsonSerialize(using = JsonDateSerializer.class)
	@JsonDeserialize(using = JsonDateDeserializer.class)
	private Date myPublished;

	/**
	 * Constructor
	 */
	@SuppressWarnings("unused")
	public StoredDetails() {
		super();
	}

	/**
	 * Constructor
	 */
	public StoredDetails(@Nonnull String theBlobId, long theBytes, @Nonnull String theContentType, HashingInputStream theIs, Date thePublished) {
		myBlobId = theBlobId;
		myBytes = theBytes;
		myContentType = theContentType;
		myHash = theIs.hash().toString();
		myPublished = thePublished;
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
			.append("blobId", myBlobId)
			.append("bytes", myBytes)
			.append("contentType", myContentType)
			.append("hash", myHash)
			.append("published", myPublished)
			.toString();
	}

	public String getHash() {
		return myHash;
	}

	public StoredDetails setHash(String theHash) {
		myHash = theHash;
		return this;
	}

	public Date getPublished() {
		return myPublished;
	}

	public StoredDetails setPublished(Date thePublished) {
		myPublished = thePublished;
		return this;
	}

	@Nonnull
	public String getContentType() {
		return myContentType;
	}

	public StoredDetails setContentType(String theContentType) {
		myContentType = theContentType;
		return this;
	}

	@Nonnull
	public String getBlobId() {
		return myBlobId;
	}

	public StoredDetails setBlobId(String theBlobId) {
		myBlobId = theBlobId;
		return this;
	}

	public long getBytes() {
		return myBytes;
	}

	public StoredDetails setBytes(long theBytes) {
		myBytes = theBytes;
		return this;
	}

}
