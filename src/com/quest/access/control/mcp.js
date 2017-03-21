var kinds, filters, orders, limits, map, onFinish, reduce, beforeMap, query_types;
var verbose = false;
var ECHO_URL = "https://constant4-dot-m-swali-hrd.appspot.com/api/cms/mcp?";

function nextData() {
    try {
        if(!map) return;
        var next = true;
        if(beforeMap)
            next = beforeMap();
        if(!next) {
            //kill the process and return
            kill_script();
            return;
        }
        var params = "request_id="+_request_id_+"&";
        if (kinds)
            params += "kinds=" + encodeURIComponent(JSON.stringify(kinds)) + "&";
        if (filters)
            params += "filters=" + encodeURIComponent(JSON.stringify(filters)) + "&";
        if (orders)
            params += "orders=" + encodeURIComponent(JSON.stringify(orders)) + "&";
        if (limits)
            params += "limits=" + encodeURIComponent(JSON.stringify(limits)) + "&";
        if (query_types)
            params += "query_types=" + encodeURIComponent(JSON.stringify(query_types));
        var result = _task_.post(ECHO_URL, params, "nextdata");
        if(verbose) _task_.sendMessage(result);

        //check if we received any valid data
        _data_ = {};
        if(result) _data_ = JSON.parse(result).results;
        if(verbose) _task_.sendMessage(JSON.stringify(_data_));
        //perform map if there is data, otherwise send a kill message
        //to notify that there is no more data to process and call on finish
        if(hasData(_data_)) 
            map();
        else
            kill_script();
    } catch(e){
        _task_.sendMessage(e);
    }
    
}

function kill_script(){
    var params = "svc=mcp_service&msg=kill_script&request_id="+_request_id_;
    _task_.post(_self_url_, params, "kill_script");
}

function hasData(obj){
    var dataFound = false;
    for (var key in obj) {
        if (obj[key].length > 0) {
            dataFound = true;
            break;
        }
    }
    return dataFound;
}

function exit(resp) {
    //get aggregator and send the data
    var postUrl = _self_url_ + "?";
    resp = encodeURIComponent(JSON.stringify(resp));
    var params = "svc=mcp_service&msg=reduce&request_id="+_request_id_+"&"+"&response="+resp;
    var result = _task_.post(postUrl, params, "reduce");
    _self_state_ = {};
    if(verbose) _task_.sendMessage(result);
    if(result) _self_state_ = JSON.parse(result).response.data;
    if(_self_state_.kill_process) {
        _task_.sendMessage("process killed prematurely, exiting");
        return; //process was killed no need to continue;
    }
    nextData();
}

//this helps to perform a query within map
function query(requestId, kinds, filters, orders, limits, query_types, onData){
    //kinds, filters, orders, limits
    if(!requestId) requestId = Math.floor(Math.random()*10000000000);
    var params = "request_id=" + requestId + "&";
    if (kinds)
        params += "kinds=" + encodeURIComponent(JSON.stringify(kinds)) + "&";
    if (filters)
        params += "filters=" + encodeURIComponent(JSON.stringify(filters)) + "&";
    if (orders)
        params += "orders=" + encodeURIComponent(JSON.stringify(orders)) + "&";
    if (limits)
        params += "limits=" + encodeURIComponent(JSON.stringify(limits)) + "&";
    if (query_types)
        params += "query_types=" + encodeURIComponent(JSON.stringify(query_types));
    var result = _task_.post(ECHO_URL, params, "query");
    if(result) result = JSON.parse(result).results;
    if(hasData(result)){
         if(onData) onData(result);
         query(kinds, filters, orders, limits, requestId, onData); //recursively go through all the results
    }
}

function graph_object(type, xkey, ykeys, labels, data){
    return JSON.stringify({
        type: type,
        data: data,
        xkey: xkey,
        ykeys: ykeys,
        labels: labels
    });
}

function getEntity(en, filterz, query_type, onEntityReady){
    kinds = [en];
    limits = {};
    limits[en] = 1;
    filters = {};
    query_types = {};
    if(query_type) query_types[en] = query_type;
    if(filterz) filters[en] = filterz;
    map = function() {
        var entity = _data_[en];
        _task_.sendMessage(JSON.stringify(entity[0]));
        if(onEntityReady) onEntityReady(entity[0]);
        _task_.sendMessage("action:app.stopPolling()");
    };
}

function plotGrowth(en, timeProp, from, to, steps){
    var start = Date.parse(from);
    var stop = Date.parse(to);
    if(stop < start){
        _task_.sendMessage("to date is less than from date");
        kill_script();
        return;
    }
    kinds = [];
    query_types = []
    filters = {};
    var separator = "#!";
    if(!steps) steps = 10;
    var incr = Math.floor((stop - start)/steps);
    var kindNames = [];
    for(var x = start; x < stop; x += incr){
        var kindName = en;
        if(x > start) kindName += en + separator + (x - start)/incr;
        kinds.push(en);
        query_types.push("count");
        var key = timeProp + " <";
        filters[kindName] = {};
        filters[kindName][key] = new Date(x).toISOString();
        filters[kindName]["_type_" + timeProp] = "datetime";
        kindNames.push(kindName);
    }
    
    map = function(){
        _task_.sendMessage("plotting growth graph for " + en);
        var entity = _data_[en];
        _task_.sendMessage("action:app.stopPolling()");
    };
    
}



