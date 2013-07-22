/*
 * Copyright 2012 hbz NRW (http://www.hbz-nrw.de/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package de.nrw.hbz.regal.api;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.multipart.MultiPart;

import de.nrw.hbz.regal.api.helper.ObjectType;

/**
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 */
@Path("/journal")
public class EJournal {
    final static Logger logger = LoggerFactory.getLogger(EJournal.class);

    Resource resources = null;

    /**
     * Creates a new Journal endpoint
     * 
     * @throws IOException
     *             if resources cannot be initalised
     */
    public EJournal() throws IOException {

	resources = new Resource();

    }

    /**
     * @return a list of journals
     */
    @GET
    @Produces({ "application/json", "application/xml" })
    public ObjectList getAll() {
	return resources.getAllOfType(ObjectType.journal.toString());

    }

    /**
     * @return a list of journals as html
     */
    @GET
    @Produces({ "text/html" })
    public Response getAllAsHtml() {
	String rem = resources
		.getAllOfTypeAsHtml(ObjectType.journal.toString());
	ResponseBuilder res = Response.ok().entity(rem);
	return res.build();
    }

    /**
     * @return a list of deleted journals
     */
    @DELETE
    @Produces({ "application/json", "application/xml" })
    public String deleteAll() {
	return resources.deleteAllOfType(ObjectType.journal.toString());

    }

    /**
     * @param pid
     *            pid of the journal to be deleted
     * @param namespace
     *            namespace of the journal
     * @return a message
     */
    @DELETE
    @Path("/{namespace}:{pid}")
    @Produces({ "application/json", "application/xml" })
    public String deleteEJournal(@PathParam("pid") String pid,
	    @PathParam("namespace") String namespace) {
	return resources.delete(pid, namespace);
    }

    /**
     * @param pid
     *            pid of the journal to be created, recommended is to use a uuid
     * @param namespace
     *            namespace of journal
     * @return a message
     */
    @PUT
    @Path("/{namespace}:{pid}")
    @Produces({ "application/json", "application/xml" })
    public String createEJournal(@PathParam("pid") String pid,
	    @PathParam("namespace") String namespace) {
	CreateObjectBean input = new CreateObjectBean();
	input.type = ObjectType.journal.toString();
	return resources.create(pid, namespace, input);

    }

    /**
     * @param pid
     *            pid
     * @return dublin core associated with the journal
     */
    @GET
    @Path("/{pid}/dc")
    @Produces({ "application/xml", "application/json" })
    public DCBeanAnnotated readEJournalDC(@PathParam("pid") String pid) {
	return resources.readDC(pid);

    }

    /**
     * @param pid
     *            the journals pid
     * @param content
     *            dublin core data
     * @return a message
     */
    @POST
    @Path("/{pid}/dc")
    @Produces({ "application/json", "application/xml" })
    @Consumes({ "application/json", "application/xml" })
    public String updateEJournalDCPost(@PathParam("pid") String pid,
	    DCBeanAnnotated content) {
	return resources.updateDC(pid, content);
    }

    /**
     * @param pid
     *            the journals pid
     * @param content
     *            dublin core data
     * @return a message
     */
    @PUT
    @Path("/{pid}/dc")
    @Produces({ "application/json", "application/xml" })
    @Consumes({ "application/json", "application/xml" })
    public String updateEJournalDCPut(@PathParam("pid") String pid,
	    DCBeanAnnotated content) {
	return resources.updateDC(pid, content);
    }

    /**
     * @param pid
     *            the journal's pid
     * @return metadata a n-triple
     */
    @GET
    @Path("/{pid}/metadata")
    @Produces({ "text/plain" })
    public String readEJournalMetadata(@PathParam("pid") String pid) {
	return resources.readMetadata(pid);
    }

    /**
     * @param pid
     *            journal's pid
     * @param content
     *            n-triple
     * @return a message
     */
    @PUT
    @Path("/{pid}/metadata")
    @Consumes({ "text/plain" })
    @Produces({ "text/plain" })
    public String updateEJournalMetadata(@PathParam("pid") String pid,
	    String content) {
	return resources.updateMetadata(pid, content);
    }

    /**
     * @param pid
     *            journal pid
     * @param content
     *            n-triple
     * @return message
     */
    @Deprecated
    @POST
    @Path("/{pid}/metadata")
    @Consumes({ "text/plain" })
    @Produces({ "text/plain" })
    public String updateEJournalMetadataPost(@PathParam("pid") String pid,
	    String content) {
	return resources.updateMetadata(pid, content);

    }

    /**
     * @param pid
     *            journal's pid
     * @return a list of volumes
     */
    @GET
    @Path("/{pid}/volume/")
    @Produces({ "application/json", "application/xml" })
    public ObjectList getAllVolumes(@PathParam("pid") String pid) {
	return resources.getAllParts(pid);
    }

    /**
     * @param pid
     *            journal's pid
     * @param namespace
     *            namespace
     * @param volumePid
     *            volume's pid
     * @return a message
     */
    @PUT
    @Path("/{pid}/volume/{namespace}:{volumePid}")
    @Produces({ "application/json", "application/xml" })
    public String createEJournalVolume(@PathParam("pid") String pid,
	    @PathParam("namespace") String namespace,
	    @PathParam("volumePid") String volumePid) {
	CreateObjectBean input = new CreateObjectBean();
	input.type = ObjectType.volume.toString();
	input.parentPid = pid;
	return resources.create(volumePid, namespace, input);
    }

    /**
     * @param pid
     *            the pid of the resource
     * @return an aggregated representation of the resource
     * @throws URISyntaxException
     */
    @GET
    @Path("/{namespace}:{pid}")
    @Produces({ "application/json", "application/xml", "text/html" })
    public Response getResource(@PathParam("pid") String pid,
	    @PathParam("namespace") String namespace) throws URISyntaxException {
	return Response
		.temporaryRedirect(
			new java.net.URI("../resource/" + namespace + ":" + pid
				+ "/about")).status(303).build();
    }

    /**
     * @param pid
     * @param volumePid
     * @param namespace
     * @return
     */
    @GET
    @Path("/{pid}/volume/{namespace}:{volumePid}/data")
    @Produces({ "application/*" })
    public Response readVolumeData(@PathParam("pid") String pid,
	    @PathParam("volumePid") String volumePid,
	    @PathParam("namespace") String namespace) {
	return resources.readData(volumePid, namespace);
    }

    /**
     * @param pid
     * @param volumePid
     * @param namespace
     * @param multiPart
     * @return
     */
    @POST
    @Path("/{pid}/volume/{namespace}:{volumePid}/data")
    @Produces({ "application/json", "application/xml" })
    @Consumes("multipart/mixed")
    public String updateVolumeData(@PathParam("pid") String pid,
	    @PathParam("volumePid") String volumePid,
	    @PathParam("namespace") String namespace, MultiPart multiPart) {
	return resources.updateData(volumePid, namespace, multiPart);
    }

    /**
     * @param pid
     * @param volumePid
     * @return
     */
    @GET
    @Path("/{pid}/volume/{volumePid}/dc")
    @Produces({ "application/xml", "application/json" })
    public DCBeanAnnotated readVolumeDC(@PathParam("pid") String pid,
	    @PathParam("volumePid") String volumePid) {
	return resources.readDC(volumePid);
    }

    /**
     * @param pid
     * @param volumePid
     * @param content
     * @return
     */
    @POST
    @Path("/{pid}/volume/{volumePid}/dc")
    @Produces({ "application/json", "application/xml" })
    @Consumes({ "application/json", "application/xml" })
    public String updateVolumeDC(@PathParam("pid") String pid,
	    @PathParam("volumePid") String volumePid, DCBeanAnnotated content) {
	return resources.updateDC(volumePid, content);
    }

    /**
     * @param pid
     * @param volumePid
     * @return
     */
    @GET
    @Path("/{pid}/volume/{volumePid}/metadata")
    @Produces({ "application/*" })
    public String readVolumeMetadata(@PathParam("pid") String pid,
	    @PathParam("volumePid") String volumePid) {
	return resources.readMetadata(volumePid);
    }

    /**
     * @param pid
     * @param volumePid
     * @param content
     * @return
     */
    @PUT
    @Path("/{pid}/volume/{volumePid}/metadata")
    @Produces({ "application/json", "application/xml" })
    @Consumes({ "text/plain" })
    public String updateVolumeMetadata(@PathParam("pid") String pid,
	    @PathParam("volumePid") String volumePid, String content) {
	return resources.updateMetadata(volumePid, content);
    }

    /**
     * @param pid
     * @param volumePid
     * @param content
     * @return
     */
    @Deprecated
    @POST
    @Path("/{pid}/volume/{volumePid}/metadata")
    @Produces({ "application/json", "application/xml" })
    @Consumes({ "text/plain" })
    public String updateVolumeMetadataPost(@PathParam("pid") String pid,
	    @PathParam("volumePid") String volumePid, String content) {
	return resources.updateMetadata(volumePid, content);
    }
}
