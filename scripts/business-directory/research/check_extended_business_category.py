"""Synthetic narrow source-category/privacy contracts; not business verification."""
import copy
import unittest
from extended_business_category import category, category_decision, core, WHITELIST


class ExtraCategoryContract(unittest.TestCase):
    def test_reviewed_extra_values_keep_exact_source_category(self):
        for key,values in WHITELIST.items():
            for value in values:self.assertEqual(f'{key}:{value}',category({key:value}))

    def test_existing_core_contacts_are_not_duplicated(self):
        tags={'shop':'sports','leisure':'fitness_centre'}
        self.assertEqual('shop:sports',core.category(tags));self.assertIsNone(category(tags))

    def test_private_no_members_and_unknown_access_are_held(self):
        for access in ('private','no','members','permit','private;customers','unknown'):
            self.assertIsNone(category({'leisure':'fitness_centre','access':access}))

    def test_explicit_customer_public_access_is_preserved(self):
        for access in ('customers','yes','public','permissive'):
            tags={'amenity':'driving_school','access':access};self.assertEqual('amenity:driving_school',category(tags))

    def test_private_commercial_ownership_is_not_private_access(self):
        tags={'leisure':'fitness_centre','ownership':'private','operator:type':'private'}
        self.assertEqual('leisure:fitness_centre',category(tags))

    def test_conditional_access_is_not_guessed(self):
        self.assertIsNone(category({'leisure':'fitness_centre','access':'yes','access:conditional':'private @ (Mo)'}))

    def test_residential_buildings_are_held_even_with_business_like_name(self):
        for value in ('house','residential','apartments','detached','dormitory'):
            self.assertIsNone(category({'leisure':'fitness_centre','building':value,'name':'Example'}))

    def test_lifecycle_flags_and_prefixed_conflicts_are_not_active_places(self):
        for extra in ({'disused':'yes'},{'construction':'yes'},{'abandoned:leisure':'fitness_centre'},
                      {'proposed:amenity':'driving_school'}):
            self.assertIsNone(category({'leisure':'fitness_centre',**extra}))

    def test_ambiguous_private_pools_clubs_and_generic_schools_stay_outside_policy(self):
        for tags in ({'leisure':'swimming_pool'},{'club':'sport'},{'amenity':'school'},
                     {'amenity':'language_school'},{'leisure':'sports_centre'},{'leisure':'sauna'}):
            self.assertIsNone(category(tags))

    def test_source_names_phones_country_and_ownership_are_never_rewritten(self):
        tags={'leisure':'fitness_centre','name':' Original ','phone':'+972123',
              'country':'PS','ownership':'private'};before=copy.deepcopy(tags)
        self.assertEqual('leisure:fitness_centre',category(tags));self.assertEqual(before,tags)
        # Category review does not decide country; unchanged geometry/country
        # rejection is still required by the source-spatial runner.
        self.assertEqual('PS',tags['country'])

    def test_missing_country_is_not_filled_by_reviewed_category(self):
        tags={'amenity':'driving_school'};category(tags);self.assertNotIn('country',tags)

    def test_case_or_unknown_category_values_are_not_guessed(self):
        for value in ('Fitness_Centre','fitness_centre;club','fitness','unknown'):
            self.assertIsNone(category({'leisure':value}))

    def test_nonboolean_lifecycle_values_are_held_without_interpretation(self):
        for flag in ('construction','proposed','disused','abandoned'):
            self.assertIsNone(category({'amenity':'driving_school',flag:'driving_school'}))

    def test_nonoperating_buildings_are_not_promoted_as_active_businesses(self):
        for building in ('construction','ruins','demolished','proposed','abandoned'):
            self.assertIsNone(category({'leisure':'fitness_centre','building':building}))

    def test_explicit_negative_lifecycle_flag_does_not_invent_inactivity(self):
        for value in ('no','false','0',''):
            self.assertEqual('amenity:driving_school',category({'amenity':'driving_school','construction':value}))


if __name__=='__main__':unittest.main(verbosity=2)
