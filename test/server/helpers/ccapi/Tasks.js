"use strict";

module.exports = Tasks;
// var cloudTools = require("CloudTools");
var _ = require("underscore");
// var logger;

// Quand le module est initialisé je peux récupérer la conf et le loggerManager
// cloudTools.initializeModule.addListener(function () {
// 	logger = cloudTools.loggerManager.getLogger("Tasks");
// });

function Tasks() {
	this.tasks = [];
	this.stopped = true;
}

Tasks.prototype.clearTasks = function () {
	console.log("CLEAR ALL TASK");
	this.tasks = [];
	if (this.timeout) {
		clearTimeout(this.timeout);
		delete this.timeout;
		this.executeTasks();
	}
	return this;
};

Tasks.prototype.addTask = function (task, name) {
	task._name = name || "";
	console.log("addtask:", task._name);
	this.tasks.push(task);
	this.onAdded();
	return this;
};

//task special qu on peut interrompre:
Tasks.prototype.addDelay = function (waitMS, name) {
	var me = this;
	var task = function (cbk) {
		clearTimeout(me.timeout);
		me.timeout = _.delay(function () {
			delete me.timeout;
			cbk();
		}, waitMS);
	};
	return this.addTask(task, name || "WAIT");
};

//tache qui efface toutes les restantes au moment de son execution
Tasks.prototype.addClearTask = function () {
	var me = this;
	var task = function (cbk) {
		me.clearTasks();
		cbk();
	};
	return this.addTask(task, "CLEAR_TASK");
};

Tasks.prototype.size = function () {
	return _.size(this.tasks);
};
Tasks.prototype.onAdded = function () {
	if (!this.stopped) {
		return; //refactor with _defer loop!
	}

	this.stopped = false;
	_.defer(this.executeTasks.bind(this));
};


Tasks.prototype.executeTasks = function () {
	var localTask = this.tasks.shift();
	if (localTask) {
		console.log(`task found: ${ localTask._name}`);
		this.stopped = false;
		var me = this;
		localTask(function (err) {
			console.log(`task end: ${ localTask._name}`);
			if (err) {
				return console.error("------------------>error during task", err.stack);
			}
			me.executeTasks();
		});
	} else {
		console.log("no long task to execute wait for new task");
		this.stopped = true;

	}
};
