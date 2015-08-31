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
package helper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import models.Globals;
import models.Node;

import org.elasticsearch.search.SearchHit;

import actions.Modify;
import actions.Read;

import com.ibm.icu.util.Calendar;

/**
 * @author Jan Schnasse
 *
 */
public class UrnAllocator implements Runnable {

    @Override
    public void run() {
	Calendar cal = Calendar.getInstance();
	cal.add(Calendar.DATE, -6);
	Date until = cal.getTime();

	cal = Calendar.getInstance();
	cal.add(Calendar.DATE, -14);
	Date from = cal.getTime();

	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	String d = dateFormat.format(until);
	play.Logger
		.info("-XPOST /utils/addUrnToAll?namespace=edoweb&snid=hbz:929:02&dateBefore="
			+ d);
	List<SearchHit> hits = new Read().list(Globals.namespaces[0], from,
		until);
	List<String> ids = hits.stream().map((SearchHit s) -> s.getId())
		.collect(Collectors.toList());
	List<Node> nodes = new Read().getNodes(ids);
	nodes.stream().forEach(n -> n.setLastModifiedBy("UrnAllocator"));
	play.Logger.info(new Modify()
		.addUrnToAll(nodes, Globals.urnSnid, until));
    }
}
