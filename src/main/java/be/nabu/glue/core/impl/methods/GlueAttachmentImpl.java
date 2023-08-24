package be.nabu.glue.core.impl.methods;

import java.util.Date;

import be.nabu.glue.api.Executor;
import be.nabu.glue.api.runs.GlueAttachment;

public class GlueAttachmentImpl implements GlueAttachment {

	private Executor executor;
	private String name, contentType, message;
	private byte[] content;
	private Date created = new Date();
	
	public GlueAttachmentImpl() {
		// auto
	}
	
	public GlueAttachmentImpl(Executor executor, String name, byte[] content, String contentType) {
		this.executor = executor;
		this.name = name;
		this.content = content;
		this.contentType = contentType;
	}
	
	public Executor getExecutor() {
		return executor;
	}
	public void setExecutor(Executor executor) {
		this.executor = executor;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getContentType() {
		return contentType;
	}
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
	public byte[] getContent() {
		return content;
	}
	public void setContent(byte[] content) {
		this.content = content;
	}
	public Date getCreated() {
		return created;
	}
	public void setCreated(Date created) {
		this.created = created;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
}
