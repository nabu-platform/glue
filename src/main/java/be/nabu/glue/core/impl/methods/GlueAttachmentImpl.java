/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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
