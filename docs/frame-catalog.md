# Frame catalogue

Six pixel interpretations of real full-suspension frames. Tube profiles, gussets, linkage details and shock placement differ. Contact points and riding parameters remain shared; choosing a frame changes its appearance.

| Frame | Model details | Manufacturer reference |
|---|---|---|
| Santa Cruz Nomad | Stepped top tube, curved down tube, low horizontal shock and lower-link details | [Nomad / VPP](https://www.santacruzbicycles.com/collections/nomad) |
| Canyon Torque AL | Angular alloy tubes, reinforced head junction, diagonal shock | [Torque AL launch](https://media-centre.canyon.com/en-INT/264505-canyon-unveils-all-new-torque-al-long-travel-bruiser/) |
| Cube Stereo ONE77 | Lower bowed top tube, substantial down tube, rocker detail | [Stereo ONE range](https://file.cube.eu/azwesc1xfg346/media/24/4f/81/1689945513/CUBE_Press_release_Stereo_ONE_EN.pdf) |
| Commencal META SX V5 | Straight alloy outline, two short rocker details and low shock | [VCS construction](https://www.commencal.com/fr/landing-vcs.html) |
| YT Capra | Sloping top tube, asymmetric reinforcement, angled shock | [Capra Core 4](https://www.yt-industries.com/en-us/Bikes/Enduro-Capra/CORE-4/) |
| Specialized Stumpjumper 15 | Slim top tube, forward down-tube bend, upper rocker and reverse shock angle | [Stumpjumper 15 frameset](https://www.specialized.com/us/en/s-works-stumpjumper-15-frameset-fox-float-genie-factory/p/4221408) |

The model details are visual interpretations of manufacturer profiles and layouts, not dimensionally exact CAD. `tools/gen_frame_catalog.py` writes the literal cube geometry. Run it before `gen_custom_assets.py` and `gen_enduro_texture.py` when changing a profile; both decal anchors and UV textures then follow the model.

Dirt frames keep a separate three-shape catalogue. Their top tubes connect directly to the seat tube; the straight variant is a sloping straight tube, and the seatstay bridge sits between the stays below the saddle.
