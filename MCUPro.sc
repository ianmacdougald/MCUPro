MCUPro {
	classvar <>port = 0;
	classvar <srcID = 1835008;

	var <server;
	var <midiout;
	var midiin;
	var <midiFuncs;
	var callibrator;
	var recorder;
	var recFunc, <>recPath;
	var player, <prevPath;
	var recording;
	var stopFunc, <playingBuffer;
	var <faderActions;
	var <vpotActions;
	var <jogAction;
	var <onActions;
	var <offActions;
	var <>channel = 0;

	*basicNew { ^super.new }

	*new {
		var instance = this.basicNew;
		if(MIDIClient.initialized, {
			instance.initMCUPro;
		}, { "MIDIClient is not initialized.".warn });
		^instance;
	}

	initMCUPro {
		var destinations = MIDIClient.destinations;

		block { | break |
			destinations.do { | point |
				if(point.name.find("MCU Pro").notNil, {
					midiout = MIDIOut(this.class.port, point.uid);
					midiin = MIDIIn.connect(this.class.port, point);
					break.value(999);
				});
			};
		};

		this.server_(Server.default);

		//Set up midi functions
		midiFuncs = IdentityDictionary.new.know_(true);
		midiFuncs.add(\noteOn -> MIDIFunc.noteOn(
			{ | velocity, note, channel, id |
				var flag = onActions[note].value;
				if(flag == 127){
					flag = 0;
				} /*else*/{
					flag = 127;
				};
				onActions[note].valueAction = flag;
				/*		buttons[index] = 1;
				midiout.noteOn(val[2], val[1], 127);
				switch(val[1],
				95, { this.record },
				94, { this.playRecording },
				93, { this.stopRecording }
				);*/
			},
			srcID: this.class.srcID
		));

		midiFuncs.add(\noteOff -> MIDIFunc.noteOff(
			{ | velocity, note, channel, id |
				var flag = offActions[note].value;
				if(flag == 127){
					flag = 0;
				} /*else*/ {
					flag = 127;
				};
				// offActions[note].valueAction = flag;
				/*offActions[note][1].value(
				offActions[note][0]
				);*/
			},
			srcID: this.class.srcID
		));

		midiFuncs.add(\bend -> MIDIFunc.bend(
			{ | bend, note |
				faderActions[note].valueAction = bend;
			},
			srcID: this.class.srcID
		));

		midiFuncs.add(\cc -> MIDIFunc.cc(
			{ | delta, note |
				var cc;
				note = note - 16;
				case
				{ note < 9 }
				{
					var toAdd = delta;
					//Vpot
					cc = vpotActions[note].value;
					if(delta >= 65){
						toAdd = delta - 65 * -1;
					};
					cc = cc + toAdd;
					vpotActions[note].delta = delta;
					vpotActions[note].valueAction = cc.clip(0.0, 127.0);
				}
				{ note == 44 }{
					cc = jogAction[0].value;
					if(delta == 1){
						cc = cc + 1;
					} /*else*/ {
						cc = cc - 1;
					};
					jogAction[0].valueAction = cc.wrap(0.0, 127.0);
				};
			},
			srcID: this.class.srcID
		));

		//Instantiate dictionary of actions
		faderActions = Array.fill(9, { | i |
			MCUAction.bend(midiout, i, {});
		});

		vpotActions = Array.fill(8, { | i |
			MCUAction.cc(midiout, i, {});
		});

		onActions = Array.fill(127, { | i |
			MCUAction.noteOn(midiout, i, {});
		});

		offActions = Array.fill(127, { | i |
			MCUAction.noteOff(midiout, i, {});
		});

		jogAction = [ MCUAction.cc(midiout, 44, {}) ];

		this.callibrate;
	}

	record {
		prevPath = recPath ?? { recorder.makePath };
		recorder.record(prevPath);
	}

	stopRecording {
		if(recorder.isRecording){
			fork{
				recorder.stopRecording;
				server.sync;
				recording = Buffer.read(
					server,
					prevPath,
				);
			}
		}
	}

	playRecording {
		recording !? {
			player = recording.play.register;
			//Turn the play and stop buttons off when the synth is done play
			player.onFree({
				MIDIIn.doNoteOffAction(
					this.class.srcID,
					94,
					0,
					0
				);

				MIDIIn.doNoteOffAction(
					this.class.srcID,
					93,
					0,
					0
				);
			});
		};
	}

	server_{ | newServer |
		server = newServer;
		recorder = Recorder(server);
	}

	callibrate { | dur(0.5) |
		//Set the faders to top then 0.
		if(callibrator.isPlaying, { callibrator.stop });
		callibrator = forkIfNeeded {
			var all = {
				| level(0) |
				(0..8).do { | i | midiout.bend(i, level) };
			};
			all.value(16383);
			dur.wait;
			all.value(0);
		};

		//Make sure all of the buttons are off.
		(0..127).do { | i |
			midiout.noteOn(0, i, 0);
		};
	}

	addFaderAction { | num(0), action({}) |
		faderActions[ num % faderActions.size ].action = action;
	}

	addOnAction { | num(0), action({}) |
		onActions[ num % onActions.size ].action = action;
	}

	addOffAction { | num(0), action({}) |
		offActions[ num % offActions.size ].action = action;
	}

	addVPotAction { | vpotNum(0), action({}) |
		vpotActions[ vpotNum % vpotActions.size ].action = action;
	}

	addJogAction { | action({}) |
		jogAction[0].action = action;
	}

	free {
		fork {
			this.callibrate;
			midiFuncs.asArray.do(_.free);
			this.disconnect;
		}
	}

	disconnect {
		//disconnect midi out and in per platform
	}

}

//Evaluates a function for each fader, vpot, transport control, and otherwise.
MCUAction {
	var <>outType, <>midiout, <>channel, <>action;
	var <>value = 0, <>delta = 1;

	*new { | outType(\noteOn), midiout, channel(0), action({}) |
		^super.newCopyArgs(outType, midiout, channel, action);
	}

	*bend { | midiout, channel, action |
		^this.new(\bend, midiout, channel, action);
	}

	*noteOn { | midiout, channel, action |
		^this.new(\noteOn, midiout, channel, action);
	}

	*noteOff { | midiout, channel, action |
		^this.new(\noteOff, midiout, channel, action);
	}

	*cc { | midiout, channel, action |
		^this.new(\cc, midiout, channel, action);
	}

	valueAction_{ | newValue |
		value = newValue;
		if(outType != \cc){
			action.value(value);
		} /*else*/ {
			action.value(value, delta);
		};
		this.outputMIDI;
	}

	outputMIDI {
		case
		{ outType == \noteOn or: { outType == \noteOff} }{
			midiout.perform(outType, 0, channel, value);
		}
		{ outType == \cc}{
			midiout.perform(\control, 0, channel, delta);
		}
		{ midiout.perform(outType, channel, value) };
	}

	clear {
		action = {};
	}
}

//Reads automation written on behalf of each fader and vpot
MCUReader { }

//Writes automation for each fader and vpot
MCUWriter { }

//Emulates the MCUPro
MCUGui { }