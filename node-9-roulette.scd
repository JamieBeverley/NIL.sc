NIL.bootConcatRoulette(corpusJsonPath:"C:/Users/jamie/AppData/Local/SuperCollider/Extensions/SuperCollider-Extensions/Nil.sc/corpus/node9-grains.json")

(
Pdef(\a,
	Pbind(
		\instrument, \grain,
		\dur, Pwhite(0.1,0.25), //  Pwhite is random btwn 0.1 and 0.25
		\turbidity,Pwhite(0,1),
		\clarity, Pwhite(0,1),
		\strength, Pwhite(0,1),
		\pitch, Pwhite(0,1), // pitchedness
		\rms, Pwhite(0,1),  // loudness
		\spectralCentroid,Pwhite(0.9,1),  // 'brightness'
		\db,-20,
		\tolerance,0.5,
		\out,[0,1],
	)
).play;
)
		