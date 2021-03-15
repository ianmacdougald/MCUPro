MCUPro {
	classvar <>port = 0;
	classvar <srcID = 1835008;
	classvar <device;
	classvar <midiin;
	classvar <midiout;
	classvar <isConnected = false;
	classvar callibrator;
	classvar <server;
	classvar <midiFuncs;
	classvar recorder;
	classvar recFunc, <>recPath;
	classvar player, <prevPath;
	classvar recording;
	classvar stopFunc, <playingBuffer;
	classvar <faderActions;
	classvar <vpotActions;
	classvar <jogAction;
	classvar <noteOnActions;
	classvar <noteOffActions;
	classvar <>channel = 0;
	classvar <>traceNoteOn = false;
	classvar <>traceNoteOff = false;
	classvar <>traceBend = false;
	classvar <>traceCC = false;

	*connect {
		forkIfNeeded {
			if(MIDIClient.initialized){

				if(isConnected.not){
					var destinations;

					/*if(midiin.notNil and: { device.notNil }){
						MIDIIn.disconnect(port, device);
					};*/

					destinations = MIDIClient.destinations;
					block { | break |
						destinations.do { | point |
							if(point.name.find("MCU Pro").notNil, {
								device = point;
								midiout = MIDIOut(port, point.uid);
								midiin = MIDIIn.connect(port, point);
								break.value(999);
							});
						};
					};

					if(midiout.isNil or: { midiin.isNil } or: { device.isNil} ){
						"Failed to connect to device.".warn;
					} /*else*/ {
						this.callibrate;
						isConnected = true;
					};

					//Store reference to the default server if needed
					server ?? { this.server = Server.default };

					//Initialize MIDIFunc objects
					this.initMIDIFuncs;

					//Instantiate dictionary of actions
					faderActions = Array.fill(9, { | i |
						MCUAction.bend(midiout, i, {});
					});

					vpotActions = Array.fill(8, { | i |
						MCUAction.cc(midiout, i, {});
					});

					noteOnActions = Array.fill(127, { | i |
						MCUAction.noteOn(midiout, i, {});
					});

					noteOffActions = Array.fill(127, { | i |
						MCUAction.noteOff(midiout, i, {});
					});

					jogAction = MCUAction.cc(midiout, 44, {});

				} /*else*/ {
					"Device already connected.".warn;
				};

			} /*else*/{
				"MIDIClient not initialized.".warn;
			}
		};
	}

	*callibrate { | dur(0.5) |
		//Set the faders to top then 0.
		if(callibrator.isPlaying, { callibrator.stop });
		callibrator = forkIfNeeded {
			var callAction = MCUAction.new(\bend, midiout, 0, {});
			//Set the faders hi
			9.do { | item |
				callAction.channel = item;
				callAction.valueAction = 16383;
			};
			(2 * dur).wait;
			//Then set the faders lo
			9.do { | item |
				callAction.channel = item;
				callAction.valueAction = 0;
			};
			//Turn off all of the buttons
			callAction.outType = \noteOn;
			127.do { | item |
				callAction.channel = item;
				callAction.valueAction = 0
			};
			//Reset all of the dials and fields
			callAction.outType = \cc;
			(48..75).do { | item |
				callAction.channel = item;
				callAction.valueAction = 0;
			};
		};
	}

	*initMIDIFuncs {
		//Set up midi functions
		midiFuncs !? { midiFuncs.asArray.do(_.free) };
		midiFuncs = IdentityDictionary.new.know_(true);
		midiFuncs.add(\noteOn -> MIDIFunc.noteOn(
			{ | velocity, note, channel, id |
				var flag = noteOnActions[note].value;
				if(flag == 127){
					flag = 0;
				} /*else*/{
					flag = 127;
				};
				noteOnActions[note].valueAction = flag;
				if(traceNoteOn){
					[ velocity, note, channel, id ].postln;
				};
				/*		buttons[index] = 1;
				midiout.noteOn(val[2], val[1], 127);
				switch(val[1],
				95, { this.record },
				94, { this.playRecording },
				93, { this.stopRecording }
				);*/
			},
			srcID: srcID
		));

		midiFuncs.add(\noteOff -> MIDIFunc.noteOff(
			{ | velocity, note, channel, id |
				var flag = noteOffActions[note].value;
				if(flag == 127){
					flag = 0;
				} /*else*/ {
					flag = 127;
				};
				// noteOffActions[note].valueAction = flag;
				/*noteOffActions[note][1].value(
				noteOffActions[note][0]
				);*/
				if(traceNoteOff){
					[ velocity, note, channel, id ].postln;
				};
			},
			srcID: srcID
		));

		midiFuncs.add(\bend -> MIDIFunc.bend(
			{ | bend, note |
				faderActions[note].valueAction = bend;
				if(traceBend){
					[ bend, note ].postln;
				};
			},
			srcID: srcID
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
					// vpotActions[note].delta = delta;
					vpotActions[note].valueAction = cc.clip(0.0, 127.0);
				}
				{ note == 44 }{
					cc = jogAction.value;
					if(delta == 1){
						cc = cc + 1;
					} /*else*/ {
						cc = cc - 1;
					};
					jogAction.valueAction = cc.wrap(0.0, 127.0);
				};
				if(traceCC){
					[ delta, note ].postln;
				};
			},
			srcID: srcID
		));
	}

	*disconnect {
		forkIfNeeded {
			this.callibrate;
			MIDIIn.disconnect(port, device);
			isConnected = false;
			midiFuncs.asArray.do(_.free);
			midiFuncs.clear;
		};
	}

	*reconnect {
		forkIfNeeded {
			this.disconnect;
			this.connect;
		};
	}

	*record {
		prevPath = recPath ?? { recorder.makePath };
		recorder.record(prevPath);
	}

	*stopRecording {
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

	*playRecording {
		recording !? {
			player = recording.play.register;
			//Turn the play and stop buttons off when the synth is done play
			player.onFree({
				MIDIIn.doNotenoteOffAction(
					srcID,
					94,
					0,
					0
				);

				MIDIIn.doNotenoteOffAction(
					srcID,
					93,
					0,
					0
				);
			});
		};
	}

	*server_{ | newServer |
		server = newServer;
		recorder = Recorder(server);
	}

	*addFaderAction { | num(0), action({}) |
		faderActions[ num % faderActions.size ].action = action;
	}

	*addNoteOnAction { | num(0), action({}) |
		noteOnActions[ num % noteOnActions.size ].action = action;
	}

	*addNoteOffAction { | num(0), action({}) |
		noteOffActions[ num % noteOffActions.size ].action = action;
	}

	*addVPotAction { | vpotNum(0), action({}) |
		vpotActions[ vpotNum % vpotActions.size ].action = action;
	}

	*addJogAction { | action({}) |
		jogAction.action = action;
	}
}

//Evaluates a function for each fader, vpot, transport control, and otherwise.
MCUAction {
	var <>outType, <>midiout, <>channel, <>action;
	var <>value = 0;

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
		^this.new(\cc, midiout, channel+48, action);
	}

	valueAction_{ | newValue |
		value = newValue;
		this.outputMIDI;
		action.value(value);
	}

	outputMIDI {
		case
		{ outType == \noteOn or: { outType == \noteOff} }{
			midiout.perform(outType, 0, channel, value);
		}
		{ outType == \cc}{
			midiout.perform(\control, 0, channel, value * 11 / 127 + 1);
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
