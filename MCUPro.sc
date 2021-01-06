MCUPro {
	classvar <>port = 0;
	classvar <srcID = 1835008;

	var <server;
	var <midiout;
	var midiin;
	var midiFuncs;
	var callibrator;
	var recorder;
	var <buttons, <faders;
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
		midiFuncs = IdentityDictionary.new;
		midiFuncs.add(\noteOn -> MIDIFunc.noteOn(
			{ | velocity, note, channel, id |
				var flag = onActions[note][0];
				if(flag == 127){
					flag = 0;
				} /*else*/{
					flag = 127;
				};
				onActions[note][0] = flag;
				onActions[note][1].value(
					onActions[note][0];
				);
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
				var flag = offActions[note][0];
				if(flag == 127){
					flag = 0;
				} /*else*/ {
					flag = 127;
				};
				offActions[note][0] = flag;
				offActions[note][1].value(
					offActions[note][0]
				);
			},
			srcID: this.class.srcID
		));

		midiFuncs.add(\bend -> MIDIFunc.bend(
			{ | bend, note |
				faderActions[note][0] = bend;
				faderActions[note][1].value(
					faderActions[note][0]
				);
			},
			srcID: this.class.srcID
		));

		midiFuncs.add(\cc -> MIDIFunc.cc(
			{ | difference, note |
				var cc;
				note = note - 16;
				case
				{ note < 9 }
				{
					//Vpot
					cc = vpotActions[note][0];
					if(difference >= 65){
						cc = cc - (difference - 65);
					} /*else*/ {
						cc = cc + difference;
					};
					vpotActions[note][0] = cc.clip(0.0, 127.0);
					vpotActions[note][1].value(vpotActions[note][0], difference);
				}
				{ note == 44 }{
					cc = jogAction[0];
					if(difference == 1){
						cc = cc + 1;
					} /*else*/ {
						cc = cc - 1;
						difference = 1;
					};
					jogAction[0] = cc.wrap(0.0, 127.0);
					jogAction[1].value(jogAction[0], 1);
					jogAction.postln;
				};
			},
			srcID: this.class.srcID
		));

		//Instantiate dictionary of actions
		faderActions = Array.fill(9, { | i |
			[ 0, MCUAction.bend(midiout, i, {}) ];
		});

		vpotActions = Array.fill(8, { | i |
			[ 0, MCUAction.cc(midiout, i, {}) ];
		});

		onActions = Array.fill(127, { | i |
			[ 0, MCUAction.noteOn(midiout, i, {}) ];
		});

		offActions = Array.fill(127, { | i |
			[ 0, MCUAction.noteOff(midiout, i, {}) ];
		});

		jogAction = [ 0, MCUAction.cc(midiout, 44, {}) ];

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

		buttons = 0 ! 127;
		faders = 0 ! 127;
	}

	addFaderAction { | faderNum(0), function({}) |
		faderActions[ faderNum % faderActions.size ][1] = function;
	}

	addOnAction { | buttonNum(0), function({}) |
		onActions[ buttonNum % onActions.size ][1] = function;
	}

	addOffAction { | buttonNum(0), function({}) |
		offActions[ buttonNum % offActions.size ][1] = function;
	}

	addVPotAction { | vpotNum(0), function({}) |
		vpotActions[ vpotNum % vpotActions.size ][1] = function;
	}

	addJogAction { | function({}) |
		jogAction[1] = function;
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
	var <>outType, <>midiout, <>channel, function;

	*new { | outType(\noteOn), midiout, channel(0), function({}) |
		^super.newCopyArgs(outType, midiout, channel, function);
	}

	*bend { | midiout, channel, function |
		^this.new(\bend, midiout, channel, function);
	}

	*noteOn { | midiout, channel, function |
		^this.new(\noteOn, midiout, channel, function);
	}

	*noteOff { | midiout, channel, function |
		^this.new(\noteOff, midiout, channel, function);
	}

	*cc { | midiout, channel, function |
		^this.new(\cc, midiout, channel, function);
	}

	value { | ... args |
		function.valueArray(args);
		case
		{ outType == \noteOn or: { outType == \noteOff } }{
			midiout.perform(outType, 0, channel, args[0]);
		}
		{ outType == \cc }{ }
		{ midiout.perform(outType, channel, args[0]) };
	}
}

//Reads automation written on behalf of each fader and vpot
MCUReader { }

//Writes automation for each fader and vpot
MCUWriter { }

//Emulates the MCUPro
MCUGui { }