/*
 * Copyright 2014 hbz NRW (http://www.hbz-nrw.de/)
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
package models;

import java.io.StringWriter;
import java.util.List;
import java.util.Vector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Jan Schnasse schnasse@hbz-nrw.de
 * 
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToScienceObject implements java.io.Serializable {

	/**
	 * @author jan schnasse
	 *
	 */
	public class Provenience {
		String createdBy = null;
		String importedFrom = null;
		String legacyId = null;
		String name = null;
		String doi = null;
		String urn = null;

		/**
		 * @param createdBy
		 */
		public void setCreatedBy(String createdBy) {
			this.createdBy = createdBy;
		}

		/**
		 * @return importedFrom
		 */
		public String getImportedFrom() {
			return importedFrom;
		}

		/**
		 * @param importedFrom
		 */
		public void setImportedFrom(String importedFrom) {
			this.importedFrom = importedFrom;
		}

		/**
		 * @return legacyId
		 */
		public String getLegacyId() {
			return legacyId;
		}

		/**
		 * @param legacyId legacyId
		 */
		public void setLegacyId(String legacyId) {
			this.legacyId = legacyId;
		}

		/**
		 * @return createdBy
		 */
		public String getCreatedBy() {
			return createdBy;
		}

		/**
		 * An internal name for the object
		 * 
		 * @param name
		 */
		public void setName(String name) {
			this.name = name;
		}

		/**
		 * 
		 * @return an internal name for the object
		 */
		public String getName() {
			return name;
		}

		/**
		 * @return the doi as string
		 */
		public String getDoi() {
			return doi;
		}

		/**
		 * @param doi
		 */
		public void setDoi(String doi) {
			this.doi = doi;
		}

		/**
		 * @return the urn as string
		 */
		public String getUrn() {
			return urn;
		}

		/**
		 * @param urn
		 */
		public void setUrn(String urn) {
			this.urn = urn;
		}

		@Override
		public int hashCode() {
			int result = 17;
			result = 31 * result + (createdBy != null ? createdBy.hashCode() : 0);
			result =
					31 * result + (importedFrom != null ? importedFrom.hashCode() : 0);
			result = 31 * result + (legacyId != null ? legacyId.hashCode() : 0);
			result = 31 * result + (name != null ? name.hashCode() : 0);
			result = 31 * result + (doi != null ? doi.hashCode() : 0);
			result = 31 * result + (urn != null ? urn.hashCode() : 0);
			return result;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Provenience))
				return false;
			Provenience mt = (Provenience) other;
			if (!(createdBy == null ? mt.createdBy == null
					: createdBy.equals(mt.createdBy)))
				return false;
			if (!(importedFrom == null ? mt.importedFrom == null
					: importedFrom.equals(mt.importedFrom)))
				return false;
			if (!(legacyId == null ? mt.legacyId == null
					: legacyId.equals(mt.legacyId)))
				return false;
			if (!(doi == null ? mt.doi == null : doi.equals(mt.doi)))
				return false;
			if (!(urn == null ? mt.urn == null : urn.equals(mt.urn)))
				return false;
			return true;
		}
	}

	String contentType = null;
	String parentPid = null;
	List<String> transformer = null;
	List<String> indexes = null;
	String accessScheme = null;
	String publishScheme = null;

	Provenience isDescribedBy = new Provenience();

	/**
	 * Default constructor
	 * 
	 */
	public ToScienceObject() {
		transformer = new Vector<String>();
		indexes = new Vector<String>();
	}

	/**
	 * @return all Transformer-Ids
	 */
	public List<String> getTransformer() {
		return transformer;
	}

	/**
	 * @param t list of Transformer-Ids
	 */
	public void setTransformer(List<String> t) {
		transformer = t;
	}

	/**
	 * @param t a valid type
	 */
	public ToScienceObject(ObjectType t) {
		contentType = t.toString();
	}

	/**
	 * @return the type of the object
	 */
	public String getContentType() {
		return contentType;
	}

	/**
	 * @param type the type
	 */
	public void setContentType(String type) {
		this.contentType = type;
	}

	/**
	 * @return the parent
	 */
	public String getParentPid() {
		return parentPid;
	}

	/**
	 * @param parentPid the parent
	 */
	public void setParentPid(String parentPid) {
		this.parentPid = parentPid;
	}

	/**
	 * @return a list of indexes, that are updated on create/modify
	 */
	public List<String> getIndexes() {
		return indexes;
	}

	/**
	 * @param indexes a list of indexes, that are updated on create/modify, valid
	 *          values so far: null, public, private
	 */
	public void setIndexes(List<String> indexes) {
		this.indexes = indexes;
	}

	/**
	 * @return a string that signals who is allowed to access the data node
	 */
	public String getAccessScheme() {
		return accessScheme;
	}

	/**
	 * @param accessScheme a string that signals who is allowed to access the data
	 *          of node
	 */
	public void setAccessScheme(String accessScheme) {
		this.accessScheme = accessScheme;
	}

	/**
	 * @return a string that signals who is allowed to access the metadata of node
	 */
	public String getPublishScheme() {
		return publishScheme;
	}

	/**
	 * @param publishScheme a string that signals who is allowed to access the
	 *          metadata node
	 */
	public void setPublishScheme(String publishScheme) {
		this.publishScheme = publishScheme;
	}

	/**
	 * @return some meta-metadata
	 */
	public Provenience getIsDescribedBy() {
		return isDescribedBy;
	}

	/**
	 * @param describedBy
	 */
	public void setIsDescribedBy(Provenience describedBy) {
		this.isDescribedBy = describedBy;
	}

	@Override
	public String toString() {
		ObjectMapper mapper = new ObjectMapper();
		StringWriter w = new StringWriter();
		try {
			mapper.writeValue(w, this);
		} catch (Exception e) {
			return super.toString();
		}
		return w.toString();
	}

	@Override
	public int hashCode() {
		int result = 17;
		result = 31 * result + (parentPid != null ? parentPid.hashCode() : 0);
		result = 31 * result + (contentType != null ? contentType.hashCode() : 0);
		result = 31 * result + (accessScheme != null ? accessScheme.hashCode() : 0);
		result = 31 * result + (transformer != null ? transformer.hashCode() : 0);
		result = 31 * result + (indexes != null ? indexes.hashCode() : 0);
		result =
				31 * result + (isDescribedBy != null ? isDescribedBy.hashCode() : 0);
		return result;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (!(other instanceof ToScienceObject))
			return false;
		ToScienceObject mt = (ToScienceObject) other;
		if (!(parentPid == null ? mt.parentPid == null
				: parentPid.equals(mt.parentPid)))
			return false;
		if (!(contentType == null ? mt.contentType == null
				: contentType.equals(mt.contentType)))
			return false;
		if (!(accessScheme == null ? mt.accessScheme == null
				: accessScheme.equals(mt.accessScheme)))
			return false;
		if (!(transformer == null ? mt.transformer == null
				: transformer.equals(mt.transformer)))
			return false;
		if (!(indexes == null ? mt.indexes == null : indexes.equals(mt.indexes)))
			return false;
		if (!(isDescribedBy == null ? mt.isDescribedBy == null
				: isDescribedBy.equals(mt.isDescribedBy)))
			return false;
		return true;
	}
}
