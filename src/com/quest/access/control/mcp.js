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
        log(JSON.stringify(_data_));
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
function query(kinds, filters, orders, limits, query_types, requestId, onData){
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
         query(kinds, filters, orders, limits, query_types, requestId, onData); //recursively go through all the results
    }
}

function graph_object(type, xkey, ykeys, labels, data){
    return {
        type: type,
        data: data,
        xkey: xkey,
        ykeys: ykeys,
        labels: labels
    };
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

function parseDate(d){
    var dt = d.split("-");
    return new Date(dt[0], dt[1], dt[2]);
}

function dateYearDiff(from, to){
    var years = parseDate(to).getFullYear() - parseDate(from).getFullYear();
    if (years <= 0)
        years = 1;
    return years;
}

//plotGrowth("Account", "create_date", "2012-01-01", "2017-01-01");
function plotGrowth(en, timeProp, from, to, steps){
    var start = parseDate(from).getTime();
    var stop = parseDate(to).getTime();
    if(stop < start){
        _task_.sendMessage("to date is less than from date");
        kill_script();
        return;
    }
    kinds = [];
    query_types = {};
    filters = {};
    var separator = "#!";
    if(!steps) steps = 10;
    var incr = Math.floor((stop - start)/steps);
    var key = timeProp + " <";    
    for(var x = start; x <= stop; x += incr){
        var kindName = en;
        kindName = en + separator + (x - start)/incr;
        kinds.push(kindName);
        query_types[kindName] = "count";
        filters[kindName] = {};
        filters[kindName][key] = new Date(x).toISOString();
        filters[kindName]["_type_" + timeProp] = "datetime";
    }
    map = function(){
        _task_.sendMessage("plotting growth graph for " + en);
        var data = [];
        var years = dateYearDiff(from, to);
        var lastCount = 0, firstCount = 0;
        for(var x = 0; x < kinds.length; x++){
            var kindName = en + separator + x;
            var count = _data_[kindName][0].count;
            if(x === 0) firstCount = count;
            else if(x === (kinds.length - 1)) lastCount = count;
            data.push({
                'time': filters[kindName][key], 
                'count':  count
            });
        }
        var g = graph_object('line', 'time', ['count'],
                ['No. of '+en+"(s)"], data);
        var growth = (lastCount - firstCount)/firstCount;
        var growthPercent = Math.round(growth*100/years);
        var nextYear = parseDate(to).getFullYear() + 1;
        var projected = Math.floor((growthPercent * lastCount)/100 + lastCount);
        g.parseTime = true;
        g.title = "A plot of "+en+" growth from "+from+" to "+to;
        g.stats = [
            ""+growthPercent+" percent annual growth", 
            "projected number of "+en+"(s) in "+nextYear+" is " + projected
        ];
        g = JSON.stringify(g);
        _task_.sendMessage("action:app.renderGraph("+g+")");
        _task_.sendMessage("action:app.stopPolling()");
    };
}

function dumpQuery(){
    log("dumping query");
    if(kinds) {
        log("kinds => " + JSON.stringify(kinds));
        //_task_.sendMessage("kinds => " + JSON.stringify(kinds));
    }
    if(filters) {
        log("filters => " + JSON.stringify(filters));
        //_task_.sendMessage("filters => " + JSON.stringify(filters));
    }
    if(orders) {
        log("orders => " + JSON.stringify(orders));
        //_task_.sendMessage("orders => " + JSON.stringify(orders));
    }
    if(limits) {
        log("limits => " + JSON.stringify(limits));
        //_task_.sendMessage("limits => " + JSON.stringify(limits));
    }
    if(query_types) { 
        log("query_types => " + JSON.stringify(query_types));
        //_task_.sendMessage("query_types => " + JSON.stringify(query_types));
    }
}

function log(msg){
    if(!msg) msg = "undefined";
    java.lang.System.out.println(msg);
}

