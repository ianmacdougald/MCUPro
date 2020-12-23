MCUPro {
	classvar <>port = 0;

	var midiout, <faders;
	var callibrator;

	*basicNew {
		^super.new;
	}

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
					break.value(999);
				});
			};
		};

		this.callibrate;
	}

	callibrate { | dur(0.5) |
		var all = {
			| level(0) |
			(0..8).do { | i | midiout.bend(i, level) };
		};

		if(callibrator.isPlaying, { callibrator.stop });
		callibrator = fork {
			all.value(16383);
			dur.wait;
			all.value(0);
		};

		faders = 0 ! 8;
	}

}

//Evaluates a function for each fader, vpot, transport control, and otherwise.
MCUAction { }

//Reads automation written on behalf of each fader and vpot
MCUReader { }

//Writes automation for each fader and vpot
MCUWriter { }

//Emulates the MCUPro
MCUGui { }