NIL {

	*out {
		arg degrees,audio;
		var nchnls = Server.default.options.numOutputBusChannels;
		var pos = (degrees+22.5)/180;
		var panned = PanAz.ar(nchnls,audio,pos:pos,orientation:0);
		^Out.ar(0,panned);
	}

}