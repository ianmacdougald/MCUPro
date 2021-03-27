//This template by default configures the MCUPro.
//fader0   -> frequency
//fader8   -> amplitude
//vpot0    -> vibrato rate
//jog dial -> vibrator depth
//play     -> starts synth
//stop     -> stops synth
//scrub    -> traces note on messages
var synth;
var freqlo = 128;
var freqhi = 2048;

//---------------------------
//FADERS---------------------
//---------------------------
MCUPro.addFaderAction(0, { arg val;
	if(synth.isPlaying){
		synth.set(\freq, val.linexp(0.0, 16383, freqlo, freqhi));
	};
});

//...

MCUPro.addFaderAction(8, { arg val;
	if(synth.isPlaying){
		synth.set(\amp, val / 16383);
	};
});

//---------------------------
//VPOTS----------------------
//---------------------------
MCUPro.addVPotAction(0, { arg val;
	if(synth.isPlaying){
		synth.set(\vibamp, val / 127 * 14.0);
	}
});

//---------------------------
//JOG------------------------
//---------------------------
MCUPro.addJogAction({ arg val;
	if(synth.isPlaying){
		synth.set(\vibrate, val.linexp(0, 127, 0.5, 128.0));
	}
});

//---------------------------
//NOTE ON--------------------
//---------------------------
//Stop button
MCUPro.setNoteOnNoAction(93, 0);
MCUPro.addNoteOnAction(93, { arg val;
	if(synth.isPlaying){
		//Free the synth
		synth.free;
	};
	//Zero faders
	MCUPro.setFaderNoAction(0, 0);
	MCUPro.setFaderNoAction(8, 0);
	//Zero vpot
	MCUPro.setVPotNoAction(0, 0);
	//Zero jog
	MCUPro.setJogNoAction(0);
	//Turn off the play button light
	MCUPro.setNoteOnNoAction(94, 0);
	//Turn off the stop button light
	fork {
		0.25.wait;
		MCUPro.setNoteOnNoAction(93, 0);
	};
});

//Play button
MCUPro.setNoteOnNoAction(94, 0);
MCUPro.addNoteOnAction(94, { arg val;
	if(synth.isPlaying.not){
		//Set faders to default positions
		MCUPro.setFaderNoAction(0, 400.explin(freqlo, freqhi, 0, 16383));
		MCUPro.setFaderNoAction(8, 0.8 * 16383);

		//Set vpot to default position
		MCUPro.setVPotNoAction(0, 127 / 14);

		//Set jog to default value
		MCUPro.setJogNoAction(5.0.explin(0.5, 128, 0.0, 127.0));

		//Play the synth
		if(Server.default.hasBooted){
			synth = play {
				SinOsc.ar(
					\freq.kr(400, 0.1) * LFTri.kr(\vibrate.kr(5.0, 0.1))
					.unipolar(\vibamp.kr(1.0, 0.1)).midiratio,
					0,
					\amp.kr(0.8, 0.1)
				) ! 2;
			};
			synth.register;
		}{ format("server '%' not running.", s).warn };
	}{
		//Engage the stop button
		MCUPro.setNoteOn(93, 127);
	};
});

//Scrub button
MCUPro.traceNoteOn = false;
MCUPro.setNoteOnNoAction(101, 0);
MCUPro.addNoteOnAction(101, {
	if(MCUPro.traceNoteOn){
		//Trace note on messages
		MCUPro.traceNoteOn = false;
	}{ MCUPro.traceNoteOn = true;}
});
