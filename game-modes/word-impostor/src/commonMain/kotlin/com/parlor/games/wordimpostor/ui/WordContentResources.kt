package com.parlor.games.wordimpostor.ui

import com.parlor.games.wordimpostor.domain.WordTopic
import com.parlor.games.wordimpostor.resources.Res
import com.parlor.games.wordimpostor.resources.wi_content_unavailable
import com.parlor.games.wordimpostor.resources.wi_question_animals_q01
import com.parlor.games.wordimpostor.resources.wi_question_animals_q02
import com.parlor.games.wordimpostor.resources.wi_question_animals_q03
import com.parlor.games.wordimpostor.resources.wi_question_animals_q04
import com.parlor.games.wordimpostor.resources.wi_question_animals_q05
import com.parlor.games.wordimpostor.resources.wi_question_animals_q06
import com.parlor.games.wordimpostor.resources.wi_question_animals_q07
import com.parlor.games.wordimpostor.resources.wi_question_animals_q08
import com.parlor.games.wordimpostor.resources.wi_question_animals_q09
import com.parlor.games.wordimpostor.resources.wi_question_animals_q10
import com.parlor.games.wordimpostor.resources.wi_question_animals_q11
import com.parlor.games.wordimpostor.resources.wi_question_animals_q12
import com.parlor.games.wordimpostor.resources.wi_question_animals_q13
import com.parlor.games.wordimpostor.resources.wi_question_animals_q14
import com.parlor.games.wordimpostor.resources.wi_question_animals_q15
import com.parlor.games.wordimpostor.resources.wi_question_animals_q16
import com.parlor.games.wordimpostor.resources.wi_question_animals_q17
import com.parlor.games.wordimpostor.resources.wi_question_animals_q18
import com.parlor.games.wordimpostor.resources.wi_question_anime_q01
import com.parlor.games.wordimpostor.resources.wi_question_anime_q02
import com.parlor.games.wordimpostor.resources.wi_question_anime_q03
import com.parlor.games.wordimpostor.resources.wi_question_anime_q04
import com.parlor.games.wordimpostor.resources.wi_question_anime_q05
import com.parlor.games.wordimpostor.resources.wi_question_anime_q06
import com.parlor.games.wordimpostor.resources.wi_question_anime_q07
import com.parlor.games.wordimpostor.resources.wi_question_anime_q08
import com.parlor.games.wordimpostor.resources.wi_question_anime_q09
import com.parlor.games.wordimpostor.resources.wi_question_anime_q10
import com.parlor.games.wordimpostor.resources.wi_question_anime_q11
import com.parlor.games.wordimpostor.resources.wi_question_anime_q12
import com.parlor.games.wordimpostor.resources.wi_question_anime_q13
import com.parlor.games.wordimpostor.resources.wi_question_anime_q14
import com.parlor.games.wordimpostor.resources.wi_question_anime_q15
import com.parlor.games.wordimpostor.resources.wi_question_anime_q16
import com.parlor.games.wordimpostor.resources.wi_question_anime_q17
import com.parlor.games.wordimpostor.resources.wi_question_anime_q18
import com.parlor.games.wordimpostor.resources.wi_question_cities_q01
import com.parlor.games.wordimpostor.resources.wi_question_cities_q02
import com.parlor.games.wordimpostor.resources.wi_question_cities_q03
import com.parlor.games.wordimpostor.resources.wi_question_cities_q04
import com.parlor.games.wordimpostor.resources.wi_question_cities_q05
import com.parlor.games.wordimpostor.resources.wi_question_cities_q06
import com.parlor.games.wordimpostor.resources.wi_question_cities_q07
import com.parlor.games.wordimpostor.resources.wi_question_cities_q08
import com.parlor.games.wordimpostor.resources.wi_question_cities_q09
import com.parlor.games.wordimpostor.resources.wi_question_cities_q10
import com.parlor.games.wordimpostor.resources.wi_question_cities_q11
import com.parlor.games.wordimpostor.resources.wi_question_cities_q12
import com.parlor.games.wordimpostor.resources.wi_question_cities_q13
import com.parlor.games.wordimpostor.resources.wi_question_cities_q14
import com.parlor.games.wordimpostor.resources.wi_question_cities_q15
import com.parlor.games.wordimpostor.resources.wi_question_cities_q16
import com.parlor.games.wordimpostor.resources.wi_question_cities_q17
import com.parlor.games.wordimpostor.resources.wi_question_cities_q18
import com.parlor.games.wordimpostor.resources.wi_question_countries_q01
import com.parlor.games.wordimpostor.resources.wi_question_countries_q02
import com.parlor.games.wordimpostor.resources.wi_question_countries_q03
import com.parlor.games.wordimpostor.resources.wi_question_countries_q04
import com.parlor.games.wordimpostor.resources.wi_question_countries_q05
import com.parlor.games.wordimpostor.resources.wi_question_countries_q06
import com.parlor.games.wordimpostor.resources.wi_question_countries_q07
import com.parlor.games.wordimpostor.resources.wi_question_countries_q08
import com.parlor.games.wordimpostor.resources.wi_question_countries_q09
import com.parlor.games.wordimpostor.resources.wi_question_countries_q10
import com.parlor.games.wordimpostor.resources.wi_question_countries_q11
import com.parlor.games.wordimpostor.resources.wi_question_countries_q12
import com.parlor.games.wordimpostor.resources.wi_question_countries_q13
import com.parlor.games.wordimpostor.resources.wi_question_countries_q14
import com.parlor.games.wordimpostor.resources.wi_question_countries_q15
import com.parlor.games.wordimpostor.resources.wi_question_countries_q16
import com.parlor.games.wordimpostor.resources.wi_question_countries_q17
import com.parlor.games.wordimpostor.resources.wi_question_countries_q18
import com.parlor.games.wordimpostor.resources.wi_question_food_q01
import com.parlor.games.wordimpostor.resources.wi_question_food_q02
import com.parlor.games.wordimpostor.resources.wi_question_food_q03
import com.parlor.games.wordimpostor.resources.wi_question_food_q04
import com.parlor.games.wordimpostor.resources.wi_question_food_q05
import com.parlor.games.wordimpostor.resources.wi_question_food_q06
import com.parlor.games.wordimpostor.resources.wi_question_food_q07
import com.parlor.games.wordimpostor.resources.wi_question_food_q08
import com.parlor.games.wordimpostor.resources.wi_question_food_q09
import com.parlor.games.wordimpostor.resources.wi_question_food_q10
import com.parlor.games.wordimpostor.resources.wi_question_food_q11
import com.parlor.games.wordimpostor.resources.wi_question_food_q12
import com.parlor.games.wordimpostor.resources.wi_question_food_q13
import com.parlor.games.wordimpostor.resources.wi_question_food_q14
import com.parlor.games.wordimpostor.resources.wi_question_food_q15
import com.parlor.games.wordimpostor.resources.wi_question_food_q16
import com.parlor.games.wordimpostor.resources.wi_question_food_q17
import com.parlor.games.wordimpostor.resources.wi_question_food_q18
import com.parlor.games.wordimpostor.resources.wi_question_football_q01
import com.parlor.games.wordimpostor.resources.wi_question_football_q02
import com.parlor.games.wordimpostor.resources.wi_question_football_q03
import com.parlor.games.wordimpostor.resources.wi_question_football_q04
import com.parlor.games.wordimpostor.resources.wi_question_football_q05
import com.parlor.games.wordimpostor.resources.wi_question_football_q06
import com.parlor.games.wordimpostor.resources.wi_question_football_q07
import com.parlor.games.wordimpostor.resources.wi_question_football_q08
import com.parlor.games.wordimpostor.resources.wi_question_football_q09
import com.parlor.games.wordimpostor.resources.wi_question_football_q10
import com.parlor.games.wordimpostor.resources.wi_question_football_q11
import com.parlor.games.wordimpostor.resources.wi_question_football_q12
import com.parlor.games.wordimpostor.resources.wi_question_football_q13
import com.parlor.games.wordimpostor.resources.wi_question_football_q14
import com.parlor.games.wordimpostor.resources.wi_question_football_q15
import com.parlor.games.wordimpostor.resources.wi_question_football_q16
import com.parlor.games.wordimpostor.resources.wi_question_football_q17
import com.parlor.games.wordimpostor.resources.wi_question_football_q18
import com.parlor.games.wordimpostor.resources.wi_question_games_q01
import com.parlor.games.wordimpostor.resources.wi_question_games_q02
import com.parlor.games.wordimpostor.resources.wi_question_games_q03
import com.parlor.games.wordimpostor.resources.wi_question_games_q04
import com.parlor.games.wordimpostor.resources.wi_question_games_q05
import com.parlor.games.wordimpostor.resources.wi_question_games_q06
import com.parlor.games.wordimpostor.resources.wi_question_games_q07
import com.parlor.games.wordimpostor.resources.wi_question_games_q08
import com.parlor.games.wordimpostor.resources.wi_question_games_q09
import com.parlor.games.wordimpostor.resources.wi_question_games_q10
import com.parlor.games.wordimpostor.resources.wi_question_games_q11
import com.parlor.games.wordimpostor.resources.wi_question_games_q12
import com.parlor.games.wordimpostor.resources.wi_question_games_q13
import com.parlor.games.wordimpostor.resources.wi_question_games_q14
import com.parlor.games.wordimpostor.resources.wi_question_games_q15
import com.parlor.games.wordimpostor.resources.wi_question_games_q16
import com.parlor.games.wordimpostor.resources.wi_question_games_q17
import com.parlor.games.wordimpostor.resources.wi_question_games_q18
import com.parlor.games.wordimpostor.resources.wi_question_movies_q01
import com.parlor.games.wordimpostor.resources.wi_question_movies_q02
import com.parlor.games.wordimpostor.resources.wi_question_movies_q03
import com.parlor.games.wordimpostor.resources.wi_question_movies_q04
import com.parlor.games.wordimpostor.resources.wi_question_movies_q05
import com.parlor.games.wordimpostor.resources.wi_question_movies_q06
import com.parlor.games.wordimpostor.resources.wi_question_movies_q07
import com.parlor.games.wordimpostor.resources.wi_question_movies_q08
import com.parlor.games.wordimpostor.resources.wi_question_movies_q09
import com.parlor.games.wordimpostor.resources.wi_question_movies_q10
import com.parlor.games.wordimpostor.resources.wi_question_movies_q11
import com.parlor.games.wordimpostor.resources.wi_question_movies_q12
import com.parlor.games.wordimpostor.resources.wi_question_movies_q13
import com.parlor.games.wordimpostor.resources.wi_question_movies_q14
import com.parlor.games.wordimpostor.resources.wi_question_movies_q15
import com.parlor.games.wordimpostor.resources.wi_question_movies_q16
import com.parlor.games.wordimpostor.resources.wi_question_movies_q17
import com.parlor.games.wordimpostor.resources.wi_question_movies_q18
import com.parlor.games.wordimpostor.resources.wi_question_people_q01
import com.parlor.games.wordimpostor.resources.wi_question_people_q02
import com.parlor.games.wordimpostor.resources.wi_question_people_q03
import com.parlor.games.wordimpostor.resources.wi_question_people_q04
import com.parlor.games.wordimpostor.resources.wi_question_people_q05
import com.parlor.games.wordimpostor.resources.wi_question_people_q06
import com.parlor.games.wordimpostor.resources.wi_question_people_q07
import com.parlor.games.wordimpostor.resources.wi_question_people_q08
import com.parlor.games.wordimpostor.resources.wi_question_people_q09
import com.parlor.games.wordimpostor.resources.wi_question_people_q10
import com.parlor.games.wordimpostor.resources.wi_question_people_q11
import com.parlor.games.wordimpostor.resources.wi_question_people_q12
import com.parlor.games.wordimpostor.resources.wi_question_people_q13
import com.parlor.games.wordimpostor.resources.wi_question_people_q14
import com.parlor.games.wordimpostor.resources.wi_question_people_q15
import com.parlor.games.wordimpostor.resources.wi_question_people_q16
import com.parlor.games.wordimpostor.resources.wi_question_people_q17
import com.parlor.games.wordimpostor.resources.wi_question_people_q18
import com.parlor.games.wordimpostor.resources.wi_question_series_q01
import com.parlor.games.wordimpostor.resources.wi_question_series_q02
import com.parlor.games.wordimpostor.resources.wi_question_series_q03
import com.parlor.games.wordimpostor.resources.wi_question_series_q04
import com.parlor.games.wordimpostor.resources.wi_question_series_q05
import com.parlor.games.wordimpostor.resources.wi_question_series_q06
import com.parlor.games.wordimpostor.resources.wi_question_series_q07
import com.parlor.games.wordimpostor.resources.wi_question_series_q08
import com.parlor.games.wordimpostor.resources.wi_question_series_q09
import com.parlor.games.wordimpostor.resources.wi_question_series_q10
import com.parlor.games.wordimpostor.resources.wi_question_series_q11
import com.parlor.games.wordimpostor.resources.wi_question_series_q12
import com.parlor.games.wordimpostor.resources.wi_question_series_q13
import com.parlor.games.wordimpostor.resources.wi_question_series_q14
import com.parlor.games.wordimpostor.resources.wi_question_series_q15
import com.parlor.games.wordimpostor.resources.wi_question_series_q16
import com.parlor.games.wordimpostor.resources.wi_question_series_q17
import com.parlor.games.wordimpostor.resources.wi_question_series_q18
import com.parlor.games.wordimpostor.resources.wi_question_sports_q01
import com.parlor.games.wordimpostor.resources.wi_question_sports_q02
import com.parlor.games.wordimpostor.resources.wi_question_sports_q03
import com.parlor.games.wordimpostor.resources.wi_question_sports_q04
import com.parlor.games.wordimpostor.resources.wi_question_sports_q05
import com.parlor.games.wordimpostor.resources.wi_question_sports_q06
import com.parlor.games.wordimpostor.resources.wi_question_sports_q07
import com.parlor.games.wordimpostor.resources.wi_question_sports_q08
import com.parlor.games.wordimpostor.resources.wi_question_sports_q09
import com.parlor.games.wordimpostor.resources.wi_question_sports_q10
import com.parlor.games.wordimpostor.resources.wi_question_sports_q11
import com.parlor.games.wordimpostor.resources.wi_question_sports_q12
import com.parlor.games.wordimpostor.resources.wi_question_sports_q13
import com.parlor.games.wordimpostor.resources.wi_question_sports_q14
import com.parlor.games.wordimpostor.resources.wi_question_sports_q15
import com.parlor.games.wordimpostor.resources.wi_question_sports_q16
import com.parlor.games.wordimpostor.resources.wi_question_sports_q17
import com.parlor.games.wordimpostor.resources.wi_question_sports_q18
import com.parlor.games.wordimpostor.resources.wi_question_technology_q01
import com.parlor.games.wordimpostor.resources.wi_question_technology_q02
import com.parlor.games.wordimpostor.resources.wi_question_technology_q03
import com.parlor.games.wordimpostor.resources.wi_question_technology_q04
import com.parlor.games.wordimpostor.resources.wi_question_technology_q05
import com.parlor.games.wordimpostor.resources.wi_question_technology_q06
import com.parlor.games.wordimpostor.resources.wi_question_technology_q07
import com.parlor.games.wordimpostor.resources.wi_question_technology_q08
import com.parlor.games.wordimpostor.resources.wi_question_technology_q09
import com.parlor.games.wordimpostor.resources.wi_question_technology_q10
import com.parlor.games.wordimpostor.resources.wi_question_technology_q11
import com.parlor.games.wordimpostor.resources.wi_question_technology_q12
import com.parlor.games.wordimpostor.resources.wi_question_technology_q13
import com.parlor.games.wordimpostor.resources.wi_question_technology_q14
import com.parlor.games.wordimpostor.resources.wi_question_technology_q15
import com.parlor.games.wordimpostor.resources.wi_question_technology_q16
import com.parlor.games.wordimpostor.resources.wi_question_technology_q17
import com.parlor.games.wordimpostor.resources.wi_question_technology_q18
import com.parlor.games.wordimpostor.resources.wi_topic_animals
import com.parlor.games.wordimpostor.resources.wi_topic_anime
import com.parlor.games.wordimpostor.resources.wi_topic_cities
import com.parlor.games.wordimpostor.resources.wi_topic_countries
import com.parlor.games.wordimpostor.resources.wi_topic_food
import com.parlor.games.wordimpostor.resources.wi_topic_football
import com.parlor.games.wordimpostor.resources.wi_topic_games
import com.parlor.games.wordimpostor.resources.wi_topic_movies
import com.parlor.games.wordimpostor.resources.wi_topic_people
import com.parlor.games.wordimpostor.resources.wi_topic_series
import com.parlor.games.wordimpostor.resources.wi_topic_sports
import com.parlor.games.wordimpostor.resources.wi_topic_technology
import com.parlor.games.wordimpostor.resources.wi_word_animals_cat
import com.parlor.games.wordimpostor.resources.wi_word_animals_cheetah
import com.parlor.games.wordimpostor.resources.wi_word_animals_dog
import com.parlor.games.wordimpostor.resources.wi_word_animals_dolphin
import com.parlor.games.wordimpostor.resources.wi_word_animals_elephant
import com.parlor.games.wordimpostor.resources.wi_word_animals_giraffe
import com.parlor.games.wordimpostor.resources.wi_word_animals_guinea_pig
import com.parlor.games.wordimpostor.resources.wi_word_animals_hamster
import com.parlor.games.wordimpostor.resources.wi_word_animals_hippo
import com.parlor.games.wordimpostor.resources.wi_word_animals_leopard
import com.parlor.games.wordimpostor.resources.wi_word_animals_lion
import com.parlor.games.wordimpostor.resources.wi_word_animals_octopus
import com.parlor.games.wordimpostor.resources.wi_word_animals_rabbit
import com.parlor.games.wordimpostor.resources.wi_word_animals_rhino
import com.parlor.games.wordimpostor.resources.wi_word_animals_sea_turtle
import com.parlor.games.wordimpostor.resources.wi_word_animals_shark
import com.parlor.games.wordimpostor.resources.wi_word_animals_tiger
import com.parlor.games.wordimpostor.resources.wi_word_animals_whale
import com.parlor.games.wordimpostor.resources.wi_word_animals_wolf
import com.parlor.games.wordimpostor.resources.wi_word_animals_zebra
import com.parlor.games.wordimpostor.resources.wi_word_anime_attack_on_titan
import com.parlor.games.wordimpostor.resources.wi_word_anime_bleach
import com.parlor.games.wordimpostor.resources.wi_word_anime_blue_lock
import com.parlor.games.wordimpostor.resources.wi_word_anime_captain_tsubasa
import com.parlor.games.wordimpostor.resources.wi_word_anime_death_note
import com.parlor.games.wordimpostor.resources.wi_word_anime_demon_slayer
import com.parlor.games.wordimpostor.resources.wi_word_anime_detective_conan
import com.parlor.games.wordimpostor.resources.wi_word_anime_digimon
import com.parlor.games.wordimpostor.resources.wi_word_anime_doraemon
import com.parlor.games.wordimpostor.resources.wi_word_anime_dragon_ball
import com.parlor.games.wordimpostor.resources.wi_word_anime_fullmetal_alchemist
import com.parlor.games.wordimpostor.resources.wi_word_anime_haikyu
import com.parlor.games.wordimpostor.resources.wi_word_anime_hunter_x_hunter
import com.parlor.games.wordimpostor.resources.wi_word_anime_jujutsu_kaisen
import com.parlor.games.wordimpostor.resources.wi_word_anime_kuroko_basketball
import com.parlor.games.wordimpostor.resources.wi_word_anime_naruto
import com.parlor.games.wordimpostor.resources.wi_word_anime_one_piece
import com.parlor.games.wordimpostor.resources.wi_word_anime_pokemon
import com.parlor.games.wordimpostor.resources.wi_word_anime_slam_dunk
import com.parlor.games.wordimpostor.resources.wi_word_anime_spy_x_family
import com.parlor.games.wordimpostor.resources.wi_word_cities_alexandria
import com.parlor.games.wordimpostor.resources.wi_word_cities_amman
import com.parlor.games.wordimpostor.resources.wi_word_cities_aswan
import com.parlor.games.wordimpostor.resources.wi_word_cities_bangkok
import com.parlor.games.wordimpostor.resources.wi_word_cities_beijing
import com.parlor.games.wordimpostor.resources.wi_word_cities_beirut
import com.parlor.games.wordimpostor.resources.wi_word_cities_berlin
import com.parlor.games.wordimpostor.resources.wi_word_cities_cairo
import com.parlor.games.wordimpostor.resources.wi_word_cities_doha
import com.parlor.games.wordimpostor.resources.wi_word_cities_dubai
import com.parlor.games.wordimpostor.resources.wi_word_cities_london
import com.parlor.games.wordimpostor.resources.wi_word_cities_luxor
import com.parlor.games.wordimpostor.resources.wi_word_cities_madrid
import com.parlor.games.wordimpostor.resources.wi_word_cities_paris
import com.parlor.games.wordimpostor.resources.wi_word_cities_port_said
import com.parlor.games.wordimpostor.resources.wi_word_cities_riyadh
import com.parlor.games.wordimpostor.resources.wi_word_cities_rome
import com.parlor.games.wordimpostor.resources.wi_word_cities_seoul
import com.parlor.games.wordimpostor.resources.wi_word_cities_singapore
import com.parlor.games.wordimpostor.resources.wi_word_cities_tokyo
import com.parlor.games.wordimpostor.resources.wi_word_countries_algeria
import com.parlor.games.wordimpostor.resources.wi_word_countries_argentina
import com.parlor.games.wordimpostor.resources.wi_word_countries_brazil
import com.parlor.games.wordimpostor.resources.wi_word_countries_canada
import com.parlor.games.wordimpostor.resources.wi_word_countries_china
import com.parlor.games.wordimpostor.resources.wi_word_countries_egypt
import com.parlor.games.wordimpostor.resources.wi_word_countries_france
import com.parlor.games.wordimpostor.resources.wi_word_countries_germany
import com.parlor.games.wordimpostor.resources.wi_word_countries_india
import com.parlor.games.wordimpostor.resources.wi_word_countries_italy
import com.parlor.games.wordimpostor.resources.wi_word_countries_japan
import com.parlor.games.wordimpostor.resources.wi_word_countries_mexico
import com.parlor.games.wordimpostor.resources.wi_word_countries_morocco
import com.parlor.games.wordimpostor.resources.wi_word_countries_portugal
import com.parlor.games.wordimpostor.resources.wi_word_countries_south_korea
import com.parlor.games.wordimpostor.resources.wi_word_countries_spain
import com.parlor.games.wordimpostor.resources.wi_word_countries_sudan
import com.parlor.games.wordimpostor.resources.wi_word_countries_thailand
import com.parlor.games.wordimpostor.resources.wi_word_countries_tunisia
import com.parlor.games.wordimpostor.resources.wi_word_countries_united_states
import com.parlor.games.wordimpostor.resources.wi_word_food_apple
import com.parlor.games.wordimpostor.resources.wi_word_food_baklava
import com.parlor.games.wordimpostor.resources.wi_word_food_banana
import com.parlor.games.wordimpostor.resources.wi_word_food_basbousa
import com.parlor.games.wordimpostor.resources.wi_word_food_burger
import com.parlor.games.wordimpostor.resources.wi_word_food_falafel
import com.parlor.games.wordimpostor.resources.wi_word_food_foul
import com.parlor.games.wordimpostor.resources.wi_word_food_koshari
import com.parlor.games.wordimpostor.resources.wi_word_food_kunafa
import com.parlor.games.wordimpostor.resources.wi_word_food_mahshi
import com.parlor.games.wordimpostor.resources.wi_word_food_mango
import com.parlor.games.wordimpostor.resources.wi_word_food_molokhia
import com.parlor.games.wordimpostor.resources.wi_word_food_pasta
import com.parlor.games.wordimpostor.resources.wi_word_food_pizza
import com.parlor.games.wordimpostor.resources.wi_word_food_rice_pudding
import com.parlor.games.wordimpostor.resources.wi_word_food_sandwich
import com.parlor.games.wordimpostor.resources.wi_word_food_shawarma
import com.parlor.games.wordimpostor.resources.wi_word_food_strawberry
import com.parlor.games.wordimpostor.resources.wi_word_food_umm_ali
import com.parlor.games.wordimpostor.resources.wi_word_food_watermelon
import com.parlor.games.wordimpostor.resources.wi_word_football_africa_cup_of_nations
import com.parlor.games.wordimpostor.resources.wi_word_football_al_ahly
import com.parlor.games.wordimpostor.resources.wi_word_football_barcelona
import com.parlor.games.wordimpostor.resources.wi_word_football_champions_league
import com.parlor.games.wordimpostor.resources.wi_word_football_club_world_cup
import com.parlor.games.wordimpostor.resources.wi_word_football_corner_kick
import com.parlor.games.wordimpostor.resources.wi_word_football_cristiano_ronaldo
import com.parlor.games.wordimpostor.resources.wi_word_football_erling_haaland
import com.parlor.games.wordimpostor.resources.wi_word_football_free_kick
import com.parlor.games.wordimpostor.resources.wi_word_football_kylian_mbappe
import com.parlor.games.wordimpostor.resources.wi_word_football_lionel_messi
import com.parlor.games.wordimpostor.resources.wi_word_football_liverpool
import com.parlor.games.wordimpostor.resources.wi_word_football_mohamed_salah
import com.parlor.games.wordimpostor.resources.wi_word_football_offside
import com.parlor.games.wordimpostor.resources.wi_word_football_penalty_kick
import com.parlor.games.wordimpostor.resources.wi_word_football_premier_league
import com.parlor.games.wordimpostor.resources.wi_word_football_real_madrid
import com.parlor.games.wordimpostor.resources.wi_word_football_throw_in
import com.parlor.games.wordimpostor.resources.wi_word_football_world_cup
import com.parlor.games.wordimpostor.resources.wi_word_football_zamalek
import com.parlor.games.wordimpostor.resources.wi_word_games_animal_crossing
import com.parlor.games.wordimpostor.resources.wi_word_games_chess
import com.parlor.games.wordimpostor.resources.wi_word_games_counter_strike
import com.parlor.games.wordimpostor.resources.wi_word_games_crash_bandicoot
import com.parlor.games.wordimpostor.resources.wi_word_games_donkey_kong
import com.parlor.games.wordimpostor.resources.wi_word_games_fortnite
import com.parlor.games.wordimpostor.resources.wi_word_games_ludo
import com.parlor.games.wordimpostor.resources.wi_word_games_minecraft
import com.parlor.games.wordimpostor.resources.wi_word_games_monopoly
import com.parlor.games.wordimpostor.resources.wi_word_games_overwatch
import com.parlor.games.wordimpostor.resources.wi_word_games_pubg
import com.parlor.games.wordimpostor.resources.wi_word_games_rayman
import com.parlor.games.wordimpostor.resources.wi_word_games_scrabble
import com.parlor.games.wordimpostor.resources.wi_word_games_sonic
import com.parlor.games.wordimpostor.resources.wi_word_games_stardew_valley
import com.parlor.games.wordimpostor.resources.wi_word_games_super_mario
import com.parlor.games.wordimpostor.resources.wi_word_games_terraria
import com.parlor.games.wordimpostor.resources.wi_word_games_the_sims
import com.parlor.games.wordimpostor.resources.wi_word_games_uno
import com.parlor.games.wordimpostor.resources.wi_word_games_valorant
import com.parlor.games.wordimpostor.resources.wi_word_movies_asal_eswed
import com.parlor.games.wordimpostor.resources.wi_word_movies_avatar
import com.parlor.games.wordimpostor.resources.wi_word_movies_el_limby
import com.parlor.games.wordimpostor.resources.wi_word_movies_el_nazer
import com.parlor.games.wordimpostor.resources.wi_word_movies_finding_nemo
import com.parlor.games.wordimpostor.resources.wi_word_movies_forrest_gump
import com.parlor.games.wordimpostor.resources.wi_word_movies_frozen
import com.parlor.games.wordimpostor.resources.wi_word_movies_gladiator
import com.parlor.games.wordimpostor.resources.wi_word_movies_inception
import com.parlor.games.wordimpostor.resources.wi_word_movies_interstellar
import com.parlor.games.wordimpostor.resources.wi_word_movies_jurassic_park
import com.parlor.games.wordimpostor.resources.wi_word_movies_saeedi_in_the_american_university
import com.parlor.games.wordimpostor.resources.wi_word_movies_shrek
import com.parlor.games.wordimpostor.resources.wi_word_movies_teer_enta
import com.parlor.games.wordimpostor.resources.wi_word_movies_the_godfather
import com.parlor.games.wordimpostor.resources.wi_word_movies_the_lion_king
import com.parlor.games.wordimpostor.resources.wi_word_movies_the_matrix
import com.parlor.games.wordimpostor.resources.wi_word_movies_the_shawshank_redemption
import com.parlor.games.wordimpostor.resources.wi_word_movies_titanic
import com.parlor.games.wordimpostor.resources.wi_word_movies_toy_story
import com.parlor.games.wordimpostor.resources.wi_word_people_adel_emam
import com.parlor.games.wordimpostor.resources.wi_word_people_ahmed_helmy
import com.parlor.games.wordimpostor.resources.wi_word_people_ahmed_mekky
import com.parlor.games.wordimpostor.resources.wi_word_people_ahmed_zewail
import com.parlor.games.wordimpostor.resources.wi_word_people_albert_einstein
import com.parlor.games.wordimpostor.resources.wi_word_people_amr_diab
import com.parlor.games.wordimpostor.resources.wi_word_people_donia_samir_ghanem
import com.parlor.games.wordimpostor.resources.wi_word_people_fairuz
import com.parlor.games.wordimpostor.resources.wi_word_people_lionel_messi
import com.parlor.games.wordimpostor.resources.wi_word_people_magdi_yacoub
import com.parlor.games.wordimpostor.resources.wi_word_people_marie_curie
import com.parlor.games.wordimpostor.resources.wi_word_people_michael_jordan
import com.parlor.games.wordimpostor.resources.wi_word_people_mohamed_henedy
import com.parlor.games.wordimpostor.resources.wi_word_people_mohamed_mounir
import com.parlor.games.wordimpostor.resources.wi_word_people_mohamed_salah
import com.parlor.games.wordimpostor.resources.wi_word_people_serena_williams
import com.parlor.games.wordimpostor.resources.wi_word_people_sherine
import com.parlor.games.wordimpostor.resources.wi_word_people_stephen_hawking
import com.parlor.games.wordimpostor.resources.wi_word_people_umm_kulthum
import com.parlor.games.wordimpostor.resources.wi_word_people_usain_bolt
import com.parlor.games.wordimpostor.resources.wi_word_series_al_kabeer_awi
import com.parlor.games.wordimpostor.resources.wi_word_series_al_wasiyya
import com.parlor.games.wordimpostor.resources.wi_word_series_better_call_saul
import com.parlor.games.wordimpostor.resources.wi_word_series_breaking_bad
import com.parlor.games.wordimpostor.resources.wi_word_series_brooklyn_nine_nine
import com.parlor.games.wordimpostor.resources.wi_word_series_dark
import com.parlor.games.wordimpostor.resources.wi_word_series_el_leaba
import com.parlor.games.wordimpostor.resources.wi_word_series_friends
import com.parlor.games.wordimpostor.resources.wi_word_series_game_of_thrones
import com.parlor.games.wordimpostor.resources.wi_word_series_modern_family
import com.parlor.games.wordimpostor.resources.wi_word_series_money_heist
import com.parlor.games.wordimpostor.resources.wi_word_series_nelly_and_sherihan
import com.parlor.games.wordimpostor.resources.wi_word_series_prison_break
import com.parlor.games.wordimpostor.resources.wi_word_series_ragel_we_set_setat
import com.parlor.games.wordimpostor.resources.wi_word_series_sherlock
import com.parlor.games.wordimpostor.resources.wi_word_series_stranger_things
import com.parlor.games.wordimpostor.resources.wi_word_series_the_big_bang_theory
import com.parlor.games.wordimpostor.resources.wi_word_series_the_office
import com.parlor.games.wordimpostor.resources.wi_word_series_the_witcher
import com.parlor.games.wordimpostor.resources.wi_word_series_wednesday
import com.parlor.games.wordimpostor.resources.wi_word_sports_badminton
import com.parlor.games.wordimpostor.resources.wi_word_sports_basketball
import com.parlor.games.wordimpostor.resources.wi_word_sports_boxing
import com.parlor.games.wordimpostor.resources.wi_word_sports_cycling
import com.parlor.games.wordimpostor.resources.wi_word_sports_handball
import com.parlor.games.wordimpostor.resources.wi_word_sports_hockey
import com.parlor.games.wordimpostor.resources.wi_word_sports_judo
import com.parlor.games.wordimpostor.resources.wi_word_sports_karate
import com.parlor.games.wordimpostor.resources.wi_word_sports_padel
import com.parlor.games.wordimpostor.resources.wi_word_sports_rowing
import com.parlor.games.wordimpostor.resources.wi_word_sports_rugby
import com.parlor.games.wordimpostor.resources.wi_word_sports_running
import com.parlor.games.wordimpostor.resources.wi_word_sports_skiing
import com.parlor.games.wordimpostor.resources.wi_word_sports_squash
import com.parlor.games.wordimpostor.resources.wi_word_sports_swimming
import com.parlor.games.wordimpostor.resources.wi_word_sports_table_tennis
import com.parlor.games.wordimpostor.resources.wi_word_sports_taekwondo
import com.parlor.games.wordimpostor.resources.wi_word_sports_tennis
import com.parlor.games.wordimpostor.resources.wi_word_sports_volleyball
import com.parlor.games.wordimpostor.resources.wi_word_sports_wrestling
import com.parlor.games.wordimpostor.resources.wi_word_technology_desktop_computer
import com.parlor.games.wordimpostor.resources.wi_word_technology_external_hard_drive
import com.parlor.games.wordimpostor.resources.wi_word_technology_game_controller
import com.parlor.games.wordimpostor.resources.wi_word_technology_headphones
import com.parlor.games.wordimpostor.resources.wi_word_technology_keyboard
import com.parlor.games.wordimpostor.resources.wi_word_technology_laptop
import com.parlor.games.wordimpostor.resources.wi_word_technology_memory_card
import com.parlor.games.wordimpostor.resources.wi_word_technology_microphone
import com.parlor.games.wordimpostor.resources.wi_word_technology_mouse
import com.parlor.games.wordimpostor.resources.wi_word_technology_power_bank
import com.parlor.games.wordimpostor.resources.wi_word_technology_projector
import com.parlor.games.wordimpostor.resources.wi_word_technology_smartphone
import com.parlor.games.wordimpostor.resources.wi_word_technology_smartwatch
import com.parlor.games.wordimpostor.resources.wi_word_technology_speaker
import com.parlor.games.wordimpostor.resources.wi_word_technology_stylus
import com.parlor.games.wordimpostor.resources.wi_word_technology_tablet
import com.parlor.games.wordimpostor.resources.wi_word_technology_touchpad
import com.parlor.games.wordimpostor.resources.wi_word_technology_usb_drive
import com.parlor.games.wordimpostor.resources.wi_word_technology_webcam
import com.parlor.games.wordimpostor.resources.wi_word_technology_wi_fi_router
import org.jetbrains.compose.resources.StringResource

internal object WordContentResources {
    fun title(topic: WordTopic): StringResource = when (topic) {
        WordTopic.Food -> Res.string.wi_topic_food
        WordTopic.Series -> Res.string.wi_topic_series
        WordTopic.Movies -> Res.string.wi_topic_movies
        WordTopic.Football -> Res.string.wi_topic_football
        WordTopic.Sports -> Res.string.wi_topic_sports
        WordTopic.Anime -> Res.string.wi_topic_anime
        WordTopic.Animals -> Res.string.wi_topic_animals
        WordTopic.Countries -> Res.string.wi_topic_countries
        WordTopic.Cities -> Res.string.wi_topic_cities
        WordTopic.Games -> Res.string.wi_topic_games
        WordTopic.Technology -> Res.string.wi_topic_technology
        WordTopic.People -> Res.string.wi_topic_people
    }
    fun word(id: String): StringResource = words[id] ?: Res.string.wi_content_unavailable
    fun question(id: String): StringResource = questions[id] ?: Res.string.wi_content_unavailable
    private val words = mapOf(
        "food_pizza" to Res.string.wi_word_food_pizza,
        "food_burger" to Res.string.wi_word_food_burger,
        "food_pasta" to Res.string.wi_word_food_pasta,
        "food_sandwich" to Res.string.wi_word_food_sandwich,
        "food_shawarma" to Res.string.wi_word_food_shawarma,
        "food_koshari" to Res.string.wi_word_food_koshari,
        "food_foul" to Res.string.wi_word_food_foul,
        "food_falafel" to Res.string.wi_word_food_falafel,
        "food_molokhia" to Res.string.wi_word_food_molokhia,
        "food_mahshi" to Res.string.wi_word_food_mahshi,
        "food_kunafa" to Res.string.wi_word_food_kunafa,
        "food_basbousa" to Res.string.wi_word_food_basbousa,
        "food_baklava" to Res.string.wi_word_food_baklava,
        "food_rice_pudding" to Res.string.wi_word_food_rice_pudding,
        "food_umm_ali" to Res.string.wi_word_food_umm_ali,
        "food_mango" to Res.string.wi_word_food_mango,
        "food_apple" to Res.string.wi_word_food_apple,
        "food_banana" to Res.string.wi_word_food_banana,
        "food_watermelon" to Res.string.wi_word_food_watermelon,
        "food_strawberry" to Res.string.wi_word_food_strawberry,
        "series_breaking_bad" to Res.string.wi_word_series_breaking_bad,
        "series_better_call_saul" to Res.string.wi_word_series_better_call_saul,
        "series_prison_break" to Res.string.wi_word_series_prison_break,
        "series_money_heist" to Res.string.wi_word_series_money_heist,
        "series_sherlock" to Res.string.wi_word_series_sherlock,
        "series_friends" to Res.string.wi_word_series_friends,
        "series_the_office" to Res.string.wi_word_series_the_office,
        "series_brooklyn_nine_nine" to Res.string.wi_word_series_brooklyn_nine_nine,
        "series_modern_family" to Res.string.wi_word_series_modern_family,
        "series_the_big_bang_theory" to Res.string.wi_word_series_the_big_bang_theory,
        "series_game_of_thrones" to Res.string.wi_word_series_game_of_thrones,
        "series_stranger_things" to Res.string.wi_word_series_stranger_things,
        "series_the_witcher" to Res.string.wi_word_series_the_witcher,
        "series_wednesday" to Res.string.wi_word_series_wednesday,
        "series_dark" to Res.string.wi_word_series_dark,
        "series_al_kabeer_awi" to Res.string.wi_word_series_al_kabeer_awi,
        "series_ragel_we_set_setat" to Res.string.wi_word_series_ragel_we_set_setat,
        "series_al_wasiyya" to Res.string.wi_word_series_al_wasiyya,
        "series_el_leaba" to Res.string.wi_word_series_el_leaba,
        "series_nelly_and_sherihan" to Res.string.wi_word_series_nelly_and_sherihan,
        "movies_inception" to Res.string.wi_word_movies_inception,
        "movies_interstellar" to Res.string.wi_word_movies_interstellar,
        "movies_avatar" to Res.string.wi_word_movies_avatar,
        "movies_jurassic_park" to Res.string.wi_word_movies_jurassic_park,
        "movies_the_matrix" to Res.string.wi_word_movies_the_matrix,
        "movies_titanic" to Res.string.wi_word_movies_titanic,
        "movies_forrest_gump" to Res.string.wi_word_movies_forrest_gump,
        "movies_the_shawshank_redemption" to Res.string.wi_word_movies_the_shawshank_redemption,
        "movies_gladiator" to Res.string.wi_word_movies_gladiator,
        "movies_the_godfather" to Res.string.wi_word_movies_the_godfather,
        "movies_toy_story" to Res.string.wi_word_movies_toy_story,
        "movies_finding_nemo" to Res.string.wi_word_movies_finding_nemo,
        "movies_the_lion_king" to Res.string.wi_word_movies_the_lion_king,
        "movies_shrek" to Res.string.wi_word_movies_shrek,
        "movies_frozen" to Res.string.wi_word_movies_frozen,
        "movies_el_limby" to Res.string.wi_word_movies_el_limby,
        "movies_asal_eswed" to Res.string.wi_word_movies_asal_eswed,
        "movies_el_nazer" to Res.string.wi_word_movies_el_nazer,
        "movies_saeedi_in_the_american_university" to Res.string.wi_word_movies_saeedi_in_the_american_university,
        "movies_teer_enta" to Res.string.wi_word_movies_teer_enta,
        "football_mohamed_salah" to Res.string.wi_word_football_mohamed_salah,
        "football_lionel_messi" to Res.string.wi_word_football_lionel_messi,
        "football_cristiano_ronaldo" to Res.string.wi_word_football_cristiano_ronaldo,
        "football_kylian_mbappe" to Res.string.wi_word_football_kylian_mbappe,
        "football_erling_haaland" to Res.string.wi_word_football_erling_haaland,
        "football_al_ahly" to Res.string.wi_word_football_al_ahly,
        "football_zamalek" to Res.string.wi_word_football_zamalek,
        "football_liverpool" to Res.string.wi_word_football_liverpool,
        "football_barcelona" to Res.string.wi_word_football_barcelona,
        "football_real_madrid" to Res.string.wi_word_football_real_madrid,
        "football_world_cup" to Res.string.wi_word_football_world_cup,
        "football_champions_league" to Res.string.wi_word_football_champions_league,
        "football_africa_cup_of_nations" to Res.string.wi_word_football_africa_cup_of_nations,
        "football_premier_league" to Res.string.wi_word_football_premier_league,
        "football_club_world_cup" to Res.string.wi_word_football_club_world_cup,
        "football_penalty_kick" to Res.string.wi_word_football_penalty_kick,
        "football_corner_kick" to Res.string.wi_word_football_corner_kick,
        "football_free_kick" to Res.string.wi_word_football_free_kick,
        "football_throw_in" to Res.string.wi_word_football_throw_in,
        "football_offside" to Res.string.wi_word_football_offside,
        "sports_basketball" to Res.string.wi_word_sports_basketball,
        "sports_volleyball" to Res.string.wi_word_sports_volleyball,
        "sports_handball" to Res.string.wi_word_sports_handball,
        "sports_rugby" to Res.string.wi_word_sports_rugby,
        "sports_hockey" to Res.string.wi_word_sports_hockey,
        "sports_tennis" to Res.string.wi_word_sports_tennis,
        "sports_squash" to Res.string.wi_word_sports_squash,
        "sports_badminton" to Res.string.wi_word_sports_badminton,
        "sports_table_tennis" to Res.string.wi_word_sports_table_tennis,
        "sports_padel" to Res.string.wi_word_sports_padel,
        "sports_swimming" to Res.string.wi_word_sports_swimming,
        "sports_running" to Res.string.wi_word_sports_running,
        "sports_cycling" to Res.string.wi_word_sports_cycling,
        "sports_rowing" to Res.string.wi_word_sports_rowing,
        "sports_skiing" to Res.string.wi_word_sports_skiing,
        "sports_boxing" to Res.string.wi_word_sports_boxing,
        "sports_karate" to Res.string.wi_word_sports_karate,
        "sports_judo" to Res.string.wi_word_sports_judo,
        "sports_taekwondo" to Res.string.wi_word_sports_taekwondo,
        "sports_wrestling" to Res.string.wi_word_sports_wrestling,
        "anime_naruto" to Res.string.wi_word_anime_naruto,
        "anime_one_piece" to Res.string.wi_word_anime_one_piece,
        "anime_dragon_ball" to Res.string.wi_word_anime_dragon_ball,
        "anime_bleach" to Res.string.wi_word_anime_bleach,
        "anime_hunter_x_hunter" to Res.string.wi_word_anime_hunter_x_hunter,
        "anime_attack_on_titan" to Res.string.wi_word_anime_attack_on_titan,
        "anime_death_note" to Res.string.wi_word_anime_death_note,
        "anime_demon_slayer" to Res.string.wi_word_anime_demon_slayer,
        "anime_jujutsu_kaisen" to Res.string.wi_word_anime_jujutsu_kaisen,
        "anime_fullmetal_alchemist" to Res.string.wi_word_anime_fullmetal_alchemist,
        "anime_haikyu" to Res.string.wi_word_anime_haikyu,
        "anime_slam_dunk" to Res.string.wi_word_anime_slam_dunk,
        "anime_blue_lock" to Res.string.wi_word_anime_blue_lock,
        "anime_captain_tsubasa" to Res.string.wi_word_anime_captain_tsubasa,
        "anime_kuroko_basketball" to Res.string.wi_word_anime_kuroko_basketball,
        "anime_pokemon" to Res.string.wi_word_anime_pokemon,
        "anime_digimon" to Res.string.wi_word_anime_digimon,
        "anime_detective_conan" to Res.string.wi_word_anime_detective_conan,
        "anime_doraemon" to Res.string.wi_word_anime_doraemon,
        "anime_spy_x_family" to Res.string.wi_word_anime_spy_x_family,
        "animals_lion" to Res.string.wi_word_animals_lion,
        "animals_tiger" to Res.string.wi_word_animals_tiger,
        "animals_leopard" to Res.string.wi_word_animals_leopard,
        "animals_cheetah" to Res.string.wi_word_animals_cheetah,
        "animals_wolf" to Res.string.wi_word_animals_wolf,
        "animals_elephant" to Res.string.wi_word_animals_elephant,
        "animals_giraffe" to Res.string.wi_word_animals_giraffe,
        "animals_zebra" to Res.string.wi_word_animals_zebra,
        "animals_rhino" to Res.string.wi_word_animals_rhino,
        "animals_hippo" to Res.string.wi_word_animals_hippo,
        "animals_cat" to Res.string.wi_word_animals_cat,
        "animals_dog" to Res.string.wi_word_animals_dog,
        "animals_rabbit" to Res.string.wi_word_animals_rabbit,
        "animals_hamster" to Res.string.wi_word_animals_hamster,
        "animals_guinea_pig" to Res.string.wi_word_animals_guinea_pig,
        "animals_dolphin" to Res.string.wi_word_animals_dolphin,
        "animals_whale" to Res.string.wi_word_animals_whale,
        "animals_shark" to Res.string.wi_word_animals_shark,
        "animals_octopus" to Res.string.wi_word_animals_octopus,
        "animals_sea_turtle" to Res.string.wi_word_animals_sea_turtle,
        "countries_egypt" to Res.string.wi_word_countries_egypt,
        "countries_morocco" to Res.string.wi_word_countries_morocco,
        "countries_tunisia" to Res.string.wi_word_countries_tunisia,
        "countries_algeria" to Res.string.wi_word_countries_algeria,
        "countries_sudan" to Res.string.wi_word_countries_sudan,
        "countries_france" to Res.string.wi_word_countries_france,
        "countries_italy" to Res.string.wi_word_countries_italy,
        "countries_spain" to Res.string.wi_word_countries_spain,
        "countries_germany" to Res.string.wi_word_countries_germany,
        "countries_portugal" to Res.string.wi_word_countries_portugal,
        "countries_japan" to Res.string.wi_word_countries_japan,
        "countries_china" to Res.string.wi_word_countries_china,
        "countries_south_korea" to Res.string.wi_word_countries_south_korea,
        "countries_india" to Res.string.wi_word_countries_india,
        "countries_thailand" to Res.string.wi_word_countries_thailand,
        "countries_brazil" to Res.string.wi_word_countries_brazil,
        "countries_argentina" to Res.string.wi_word_countries_argentina,
        "countries_mexico" to Res.string.wi_word_countries_mexico,
        "countries_canada" to Res.string.wi_word_countries_canada,
        "countries_united_states" to Res.string.wi_word_countries_united_states,
        "cities_cairo" to Res.string.wi_word_cities_cairo,
        "cities_alexandria" to Res.string.wi_word_cities_alexandria,
        "cities_luxor" to Res.string.wi_word_cities_luxor,
        "cities_aswan" to Res.string.wi_word_cities_aswan,
        "cities_port_said" to Res.string.wi_word_cities_port_said,
        "cities_paris" to Res.string.wi_word_cities_paris,
        "cities_london" to Res.string.wi_word_cities_london,
        "cities_rome" to Res.string.wi_word_cities_rome,
        "cities_madrid" to Res.string.wi_word_cities_madrid,
        "cities_berlin" to Res.string.wi_word_cities_berlin,
        "cities_tokyo" to Res.string.wi_word_cities_tokyo,
        "cities_seoul" to Res.string.wi_word_cities_seoul,
        "cities_beijing" to Res.string.wi_word_cities_beijing,
        "cities_bangkok" to Res.string.wi_word_cities_bangkok,
        "cities_singapore" to Res.string.wi_word_cities_singapore,
        "cities_dubai" to Res.string.wi_word_cities_dubai,
        "cities_doha" to Res.string.wi_word_cities_doha,
        "cities_riyadh" to Res.string.wi_word_cities_riyadh,
        "cities_beirut" to Res.string.wi_word_cities_beirut,
        "cities_amman" to Res.string.wi_word_cities_amman,
        "games_minecraft" to Res.string.wi_word_games_minecraft,
        "games_terraria" to Res.string.wi_word_games_terraria,
        "games_stardew_valley" to Res.string.wi_word_games_stardew_valley,
        "games_animal_crossing" to Res.string.wi_word_games_animal_crossing,
        "games_the_sims" to Res.string.wi_word_games_the_sims,
        "games_fortnite" to Res.string.wi_word_games_fortnite,
        "games_pubg" to Res.string.wi_word_games_pubg,
        "games_valorant" to Res.string.wi_word_games_valorant,
        "games_counter_strike" to Res.string.wi_word_games_counter_strike,
        "games_overwatch" to Res.string.wi_word_games_overwatch,
        "games_chess" to Res.string.wi_word_games_chess,
        "games_monopoly" to Res.string.wi_word_games_monopoly,
        "games_scrabble" to Res.string.wi_word_games_scrabble,
        "games_uno" to Res.string.wi_word_games_uno,
        "games_ludo" to Res.string.wi_word_games_ludo,
        "games_super_mario" to Res.string.wi_word_games_super_mario,
        "games_sonic" to Res.string.wi_word_games_sonic,
        "games_crash_bandicoot" to Res.string.wi_word_games_crash_bandicoot,
        "games_rayman" to Res.string.wi_word_games_rayman,
        "games_donkey_kong" to Res.string.wi_word_games_donkey_kong,
        "technology_smartphone" to Res.string.wi_word_technology_smartphone,
        "technology_tablet" to Res.string.wi_word_technology_tablet,
        "technology_laptop" to Res.string.wi_word_technology_laptop,
        "technology_desktop_computer" to Res.string.wi_word_technology_desktop_computer,
        "technology_smartwatch" to Res.string.wi_word_technology_smartwatch,
        "technology_headphones" to Res.string.wi_word_technology_headphones,
        "technology_speaker" to Res.string.wi_word_technology_speaker,
        "technology_microphone" to Res.string.wi_word_technology_microphone,
        "technology_webcam" to Res.string.wi_word_technology_webcam,
        "technology_projector" to Res.string.wi_word_technology_projector,
        "technology_keyboard" to Res.string.wi_word_technology_keyboard,
        "technology_mouse" to Res.string.wi_word_technology_mouse,
        "technology_game_controller" to Res.string.wi_word_technology_game_controller,
        "technology_stylus" to Res.string.wi_word_technology_stylus,
        "technology_touchpad" to Res.string.wi_word_technology_touchpad,
        "technology_wi_fi_router" to Res.string.wi_word_technology_wi_fi_router,
        "technology_power_bank" to Res.string.wi_word_technology_power_bank,
        "technology_usb_drive" to Res.string.wi_word_technology_usb_drive,
        "technology_external_hard_drive" to Res.string.wi_word_technology_external_hard_drive,
        "technology_memory_card" to Res.string.wi_word_technology_memory_card,
        "people_adel_emam" to Res.string.wi_word_people_adel_emam,
        "people_ahmed_helmy" to Res.string.wi_word_people_ahmed_helmy,
        "people_ahmed_mekky" to Res.string.wi_word_people_ahmed_mekky,
        "people_mohamed_henedy" to Res.string.wi_word_people_mohamed_henedy,
        "people_donia_samir_ghanem" to Res.string.wi_word_people_donia_samir_ghanem,
        "people_umm_kulthum" to Res.string.wi_word_people_umm_kulthum,
        "people_amr_diab" to Res.string.wi_word_people_amr_diab,
        "people_mohamed_mounir" to Res.string.wi_word_people_mohamed_mounir,
        "people_sherine" to Res.string.wi_word_people_sherine,
        "people_fairuz" to Res.string.wi_word_people_fairuz,
        "people_mohamed_salah" to Res.string.wi_word_people_mohamed_salah,
        "people_lionel_messi" to Res.string.wi_word_people_lionel_messi,
        "people_serena_williams" to Res.string.wi_word_people_serena_williams,
        "people_michael_jordan" to Res.string.wi_word_people_michael_jordan,
        "people_usain_bolt" to Res.string.wi_word_people_usain_bolt,
        "people_albert_einstein" to Res.string.wi_word_people_albert_einstein,
        "people_marie_curie" to Res.string.wi_word_people_marie_curie,
        "people_ahmed_zewail" to Res.string.wi_word_people_ahmed_zewail,
        "people_magdi_yacoub" to Res.string.wi_word_people_magdi_yacoub,
        "people_stephen_hawking" to Res.string.wi_word_people_stephen_hawking
    )
    private val questions = mapOf(
        "food_q01" to Res.string.wi_question_food_q01,
        "food_q02" to Res.string.wi_question_food_q02,
        "food_q03" to Res.string.wi_question_food_q03,
        "food_q04" to Res.string.wi_question_food_q04,
        "food_q05" to Res.string.wi_question_food_q05,
        "food_q06" to Res.string.wi_question_food_q06,
        "food_q07" to Res.string.wi_question_food_q07,
        "food_q08" to Res.string.wi_question_food_q08,
        "food_q09" to Res.string.wi_question_food_q09,
        "food_q10" to Res.string.wi_question_food_q10,
        "food_q11" to Res.string.wi_question_food_q11,
        "food_q12" to Res.string.wi_question_food_q12,
        "food_q13" to Res.string.wi_question_food_q13,
        "food_q14" to Res.string.wi_question_food_q14,
        "food_q15" to Res.string.wi_question_food_q15,
        "food_q16" to Res.string.wi_question_food_q16,
        "food_q17" to Res.string.wi_question_food_q17,
        "food_q18" to Res.string.wi_question_food_q18,
        "series_q01" to Res.string.wi_question_series_q01,
        "series_q02" to Res.string.wi_question_series_q02,
        "series_q03" to Res.string.wi_question_series_q03,
        "series_q04" to Res.string.wi_question_series_q04,
        "series_q05" to Res.string.wi_question_series_q05,
        "series_q06" to Res.string.wi_question_series_q06,
        "series_q07" to Res.string.wi_question_series_q07,
        "series_q08" to Res.string.wi_question_series_q08,
        "series_q09" to Res.string.wi_question_series_q09,
        "series_q10" to Res.string.wi_question_series_q10,
        "series_q11" to Res.string.wi_question_series_q11,
        "series_q12" to Res.string.wi_question_series_q12,
        "series_q13" to Res.string.wi_question_series_q13,
        "series_q14" to Res.string.wi_question_series_q14,
        "series_q15" to Res.string.wi_question_series_q15,
        "series_q16" to Res.string.wi_question_series_q16,
        "series_q17" to Res.string.wi_question_series_q17,
        "series_q18" to Res.string.wi_question_series_q18,
        "movies_q01" to Res.string.wi_question_movies_q01,
        "movies_q02" to Res.string.wi_question_movies_q02,
        "movies_q03" to Res.string.wi_question_movies_q03,
        "movies_q04" to Res.string.wi_question_movies_q04,
        "movies_q05" to Res.string.wi_question_movies_q05,
        "movies_q06" to Res.string.wi_question_movies_q06,
        "movies_q07" to Res.string.wi_question_movies_q07,
        "movies_q08" to Res.string.wi_question_movies_q08,
        "movies_q09" to Res.string.wi_question_movies_q09,
        "movies_q10" to Res.string.wi_question_movies_q10,
        "movies_q11" to Res.string.wi_question_movies_q11,
        "movies_q12" to Res.string.wi_question_movies_q12,
        "movies_q13" to Res.string.wi_question_movies_q13,
        "movies_q14" to Res.string.wi_question_movies_q14,
        "movies_q15" to Res.string.wi_question_movies_q15,
        "movies_q16" to Res.string.wi_question_movies_q16,
        "movies_q17" to Res.string.wi_question_movies_q17,
        "movies_q18" to Res.string.wi_question_movies_q18,
        "football_q01" to Res.string.wi_question_football_q01,
        "football_q02" to Res.string.wi_question_football_q02,
        "football_q03" to Res.string.wi_question_football_q03,
        "football_q04" to Res.string.wi_question_football_q04,
        "football_q05" to Res.string.wi_question_football_q05,
        "football_q06" to Res.string.wi_question_football_q06,
        "football_q07" to Res.string.wi_question_football_q07,
        "football_q08" to Res.string.wi_question_football_q08,
        "football_q09" to Res.string.wi_question_football_q09,
        "football_q10" to Res.string.wi_question_football_q10,
        "football_q11" to Res.string.wi_question_football_q11,
        "football_q12" to Res.string.wi_question_football_q12,
        "football_q13" to Res.string.wi_question_football_q13,
        "football_q14" to Res.string.wi_question_football_q14,
        "football_q15" to Res.string.wi_question_football_q15,
        "football_q16" to Res.string.wi_question_football_q16,
        "football_q17" to Res.string.wi_question_football_q17,
        "football_q18" to Res.string.wi_question_football_q18,
        "sports_q01" to Res.string.wi_question_sports_q01,
        "sports_q02" to Res.string.wi_question_sports_q02,
        "sports_q03" to Res.string.wi_question_sports_q03,
        "sports_q04" to Res.string.wi_question_sports_q04,
        "sports_q05" to Res.string.wi_question_sports_q05,
        "sports_q06" to Res.string.wi_question_sports_q06,
        "sports_q07" to Res.string.wi_question_sports_q07,
        "sports_q08" to Res.string.wi_question_sports_q08,
        "sports_q09" to Res.string.wi_question_sports_q09,
        "sports_q10" to Res.string.wi_question_sports_q10,
        "sports_q11" to Res.string.wi_question_sports_q11,
        "sports_q12" to Res.string.wi_question_sports_q12,
        "sports_q13" to Res.string.wi_question_sports_q13,
        "sports_q14" to Res.string.wi_question_sports_q14,
        "sports_q15" to Res.string.wi_question_sports_q15,
        "sports_q16" to Res.string.wi_question_sports_q16,
        "sports_q17" to Res.string.wi_question_sports_q17,
        "sports_q18" to Res.string.wi_question_sports_q18,
        "anime_q01" to Res.string.wi_question_anime_q01,
        "anime_q02" to Res.string.wi_question_anime_q02,
        "anime_q03" to Res.string.wi_question_anime_q03,
        "anime_q04" to Res.string.wi_question_anime_q04,
        "anime_q05" to Res.string.wi_question_anime_q05,
        "anime_q06" to Res.string.wi_question_anime_q06,
        "anime_q07" to Res.string.wi_question_anime_q07,
        "anime_q08" to Res.string.wi_question_anime_q08,
        "anime_q09" to Res.string.wi_question_anime_q09,
        "anime_q10" to Res.string.wi_question_anime_q10,
        "anime_q11" to Res.string.wi_question_anime_q11,
        "anime_q12" to Res.string.wi_question_anime_q12,
        "anime_q13" to Res.string.wi_question_anime_q13,
        "anime_q14" to Res.string.wi_question_anime_q14,
        "anime_q15" to Res.string.wi_question_anime_q15,
        "anime_q16" to Res.string.wi_question_anime_q16,
        "anime_q17" to Res.string.wi_question_anime_q17,
        "anime_q18" to Res.string.wi_question_anime_q18,
        "animals_q01" to Res.string.wi_question_animals_q01,
        "animals_q02" to Res.string.wi_question_animals_q02,
        "animals_q03" to Res.string.wi_question_animals_q03,
        "animals_q04" to Res.string.wi_question_animals_q04,
        "animals_q05" to Res.string.wi_question_animals_q05,
        "animals_q06" to Res.string.wi_question_animals_q06,
        "animals_q07" to Res.string.wi_question_animals_q07,
        "animals_q08" to Res.string.wi_question_animals_q08,
        "animals_q09" to Res.string.wi_question_animals_q09,
        "animals_q10" to Res.string.wi_question_animals_q10,
        "animals_q11" to Res.string.wi_question_animals_q11,
        "animals_q12" to Res.string.wi_question_animals_q12,
        "animals_q13" to Res.string.wi_question_animals_q13,
        "animals_q14" to Res.string.wi_question_animals_q14,
        "animals_q15" to Res.string.wi_question_animals_q15,
        "animals_q16" to Res.string.wi_question_animals_q16,
        "animals_q17" to Res.string.wi_question_animals_q17,
        "animals_q18" to Res.string.wi_question_animals_q18,
        "countries_q01" to Res.string.wi_question_countries_q01,
        "countries_q02" to Res.string.wi_question_countries_q02,
        "countries_q03" to Res.string.wi_question_countries_q03,
        "countries_q04" to Res.string.wi_question_countries_q04,
        "countries_q05" to Res.string.wi_question_countries_q05,
        "countries_q06" to Res.string.wi_question_countries_q06,
        "countries_q07" to Res.string.wi_question_countries_q07,
        "countries_q08" to Res.string.wi_question_countries_q08,
        "countries_q09" to Res.string.wi_question_countries_q09,
        "countries_q10" to Res.string.wi_question_countries_q10,
        "countries_q11" to Res.string.wi_question_countries_q11,
        "countries_q12" to Res.string.wi_question_countries_q12,
        "countries_q13" to Res.string.wi_question_countries_q13,
        "countries_q14" to Res.string.wi_question_countries_q14,
        "countries_q15" to Res.string.wi_question_countries_q15,
        "countries_q16" to Res.string.wi_question_countries_q16,
        "countries_q17" to Res.string.wi_question_countries_q17,
        "countries_q18" to Res.string.wi_question_countries_q18,
        "cities_q01" to Res.string.wi_question_cities_q01,
        "cities_q02" to Res.string.wi_question_cities_q02,
        "cities_q03" to Res.string.wi_question_cities_q03,
        "cities_q04" to Res.string.wi_question_cities_q04,
        "cities_q05" to Res.string.wi_question_cities_q05,
        "cities_q06" to Res.string.wi_question_cities_q06,
        "cities_q07" to Res.string.wi_question_cities_q07,
        "cities_q08" to Res.string.wi_question_cities_q08,
        "cities_q09" to Res.string.wi_question_cities_q09,
        "cities_q10" to Res.string.wi_question_cities_q10,
        "cities_q11" to Res.string.wi_question_cities_q11,
        "cities_q12" to Res.string.wi_question_cities_q12,
        "cities_q13" to Res.string.wi_question_cities_q13,
        "cities_q14" to Res.string.wi_question_cities_q14,
        "cities_q15" to Res.string.wi_question_cities_q15,
        "cities_q16" to Res.string.wi_question_cities_q16,
        "cities_q17" to Res.string.wi_question_cities_q17,
        "cities_q18" to Res.string.wi_question_cities_q18,
        "games_q01" to Res.string.wi_question_games_q01,
        "games_q02" to Res.string.wi_question_games_q02,
        "games_q03" to Res.string.wi_question_games_q03,
        "games_q04" to Res.string.wi_question_games_q04,
        "games_q05" to Res.string.wi_question_games_q05,
        "games_q06" to Res.string.wi_question_games_q06,
        "games_q07" to Res.string.wi_question_games_q07,
        "games_q08" to Res.string.wi_question_games_q08,
        "games_q09" to Res.string.wi_question_games_q09,
        "games_q10" to Res.string.wi_question_games_q10,
        "games_q11" to Res.string.wi_question_games_q11,
        "games_q12" to Res.string.wi_question_games_q12,
        "games_q13" to Res.string.wi_question_games_q13,
        "games_q14" to Res.string.wi_question_games_q14,
        "games_q15" to Res.string.wi_question_games_q15,
        "games_q16" to Res.string.wi_question_games_q16,
        "games_q17" to Res.string.wi_question_games_q17,
        "games_q18" to Res.string.wi_question_games_q18,
        "technology_q01" to Res.string.wi_question_technology_q01,
        "technology_q02" to Res.string.wi_question_technology_q02,
        "technology_q03" to Res.string.wi_question_technology_q03,
        "technology_q04" to Res.string.wi_question_technology_q04,
        "technology_q05" to Res.string.wi_question_technology_q05,
        "technology_q06" to Res.string.wi_question_technology_q06,
        "technology_q07" to Res.string.wi_question_technology_q07,
        "technology_q08" to Res.string.wi_question_technology_q08,
        "technology_q09" to Res.string.wi_question_technology_q09,
        "technology_q10" to Res.string.wi_question_technology_q10,
        "technology_q11" to Res.string.wi_question_technology_q11,
        "technology_q12" to Res.string.wi_question_technology_q12,
        "technology_q13" to Res.string.wi_question_technology_q13,
        "technology_q14" to Res.string.wi_question_technology_q14,
        "technology_q15" to Res.string.wi_question_technology_q15,
        "technology_q16" to Res.string.wi_question_technology_q16,
        "technology_q17" to Res.string.wi_question_technology_q17,
        "technology_q18" to Res.string.wi_question_technology_q18,
        "people_q01" to Res.string.wi_question_people_q01,
        "people_q02" to Res.string.wi_question_people_q02,
        "people_q03" to Res.string.wi_question_people_q03,
        "people_q04" to Res.string.wi_question_people_q04,
        "people_q05" to Res.string.wi_question_people_q05,
        "people_q06" to Res.string.wi_question_people_q06,
        "people_q07" to Res.string.wi_question_people_q07,
        "people_q08" to Res.string.wi_question_people_q08,
        "people_q09" to Res.string.wi_question_people_q09,
        "people_q10" to Res.string.wi_question_people_q10,
        "people_q11" to Res.string.wi_question_people_q11,
        "people_q12" to Res.string.wi_question_people_q12,
        "people_q13" to Res.string.wi_question_people_q13,
        "people_q14" to Res.string.wi_question_people_q14,
        "people_q15" to Res.string.wi_question_people_q15,
        "people_q16" to Res.string.wi_question_people_q16,
        "people_q17" to Res.string.wi_question_people_q17,
        "people_q18" to Res.string.wi_question_people_q18
    )
}
