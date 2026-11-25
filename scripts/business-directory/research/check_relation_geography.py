"""Adversarial role-aware multipolygon research checks, not business verification."""
import copy
import math
import unittest

from research_relation_geography import RelationScope, source_rings, AssemblyReject


def square(x, y, size):
    return [[x, y], [x + size, y], [x + size, y + size], [x, y + size], [x, y]]


def regions():
    return {key: [{"coordinates": value}] for key, value in {
        "source_IL": square(0, 0, 10), "Gaza_admin4": square(2, 2, 1),
        "JudeaSamaria_admin4_outer_filled": square(6, 6, 1),
        "WestBank_historic_outer_filled": square(7, 6, 1),
    }.items()}


def fixture(parts):
    members, ways, nodes = [], {}, {}
    next_ref = 1
    for way_id, (role, coords) in enumerate(parts, 1):
        refs = []
        closed = coords[0] == coords[-1]
        values = coords[:-1] if closed else coords
        for lon, lat in values:
            ref = next_ref
            next_ref += 1
            refs.append(ref)
            nodes[str(ref)] = dict(source_id=f"osm:node:{ref}", longitude=lon,
                                   latitude=lat, valid_coordinates=True)
        if closed:
            refs.append(refs[0])
        members.append(dict(type="w", ref=way_id, role=role))
        ways[str(way_id)] = dict(source_id=f"osm:way:{way_id}", node_refs=refs)
    return members, ways, nodes


class RoleAwareGeometryContract(unittest.TestCase):
    def setUp(self):
        self.scope = RelationScope(regions())

    def tearDown(self):
        self.scope.close()

    def classify(self, parts):
        return self.scope.classify_relation(*fixture(parts), {"type": "multipolygon"})

    def test_source_hole_is_preserved_and_not_filled(self):
        decision, geometry = self.classify([("outer", square(4, 4, 1)), ("inner", square(4.2, 4.2, 0.2))])
        self.assertEqual("research-qualified-complete-role-aware-multipolygon", decision)
        self.assertEqual([0], geometry["inner_to_outer_indices"])
        self.assertEqual(1, geometry["inner_count"])
        polygon, _ = self.scope.build_geometry(geometry)
        point = self.scope.g.GEOSWKTReader_read(self.scope.reader, b"POINT (4.3 4.3)")
        try:
            self.assertFalse(self.scope.predicate("GEOSIntersects", polygon, point))
        finally:
            self.scope.g.GEOSGeom_destroy(point)
            self.scope.g.GEOSGeom_destroy(polygon)

    def test_unambiguous_open_segments_join_only_matching_refs_with_reversal(self):
        members = [dict(type="w", ref=1, role="outer"), dict(type="w", ref=2, role="outer")]
        ways = {"1": dict(source_id="osm:way:1", node_refs=[1, 2, 3]),
                "2": dict(source_id="osm:way:2", node_refs=[1, 4, 3])}
        nodes = {str(i): dict(source_id=f"osm:node:{i}", longitude=point[0], latitude=point[1], valid_coordinates=True)
                 for i, point in enumerate(square(4, 4, 0.2)[:-1], 1)}
        decision, geometry = self.scope.classify_relation(members, ways, nodes, {"type": "multipolygon"})
        self.assertEqual("research-qualified-complete-role-aware-multipolygon", decision)
        self.assertEqual([1, 2, 3, 4, 1], geometry["outer"][0]["node_refs"])
        self.assertEqual([{"ref": 1, "direction": "source"}, {"ref": 2, "direction": "reversed"}], geometry["outer"][0]["member_traversal"])

    def test_disjoint_source_outer_islands_preserved(self):
        decision, geometry = self.classify([("outer", square(4, 4, 0.2)), ("outer", square(5, 4, 0.2))])
        self.assertEqual("research-qualified-complete-role-aware-multipolygon", decision)
        self.assertEqual(2, geometry["outer_count"])

    def test_repeated_source_way_is_rejected_even_if_role_changes(self):
        members, ways, nodes = fixture([("outer", square(4, 4, 1))])
        members.append(dict(type="w", ref=1, role="inner"))
        self.assertEqual("reject-repeated-source-member-way", self.scope.classify_relation(members, ways, nodes, {"type": "multipolygon"})[0])

    def test_nested_nonway_unknown_empty_roles_are_not_guessed(self):
        for kind, role in (("r", "outer"), ("n", "label"), ("w", ""), ("w", "unknown")):
            members, ways, nodes = fixture([("outer", square(4, 4, 1))])
            members[0].update(type=kind, role=role)
            self.assertEqual("reject-nested-non-way-or-ambiguous-role-member", self.scope.classify_relation(members, ways, nodes, {"type": "multipolygon"})[0])

    def test_missing_way_and_missing_coordinate_reject_whole_relation(self):
        members, ways, nodes = fixture([("outer", square(4, 4, 1))])
        self.assertEqual("reject-missing-or-mismatched-member-way", self.scope.classify_relation(members, {}, nodes, {"type": "multipolygon"})[0])
        del nodes["2"]
        self.assertEqual("reject-missing-or-mismatched-source-node", self.scope.classify_relation(members, ways, nodes, {"type": "multipolygon"})[0])

    def test_invalid_node_values_never_enter_geos(self):
        for value in (math.nan, math.inf, True, 181, "4"):
            members, ways, nodes = fixture([("outer", square(4, 4, 1))])
            nodes["2"]["longitude"] = value
            self.assertEqual("reject-invalid-source-node-coordinate", self.scope.classify_relation(members, ways, nodes, {"type": "multipolygon"})[0])

    def test_unclosed_segments_are_never_given_a_closing_edge(self):
        decision, _ = self.classify([("outer", [[4, 4], [5, 4], [5, 5]])])
        self.assertEqual("reject-open-or-ambiguous-role-endpoints", decision)

    def test_branching_endpoints_are_rejected(self):
        members, ways, nodes = fixture([("outer", square(4, 4, 1))])
        ways["1"]["node_refs"] = [1, 2]
        for way_id, refs in ((2, [2, 3]), (3, [2, 4]), (4, [3, 1]), (5, [4, 1])):
            members.append(dict(type="w", ref=way_id, role="outer"))
            ways[str(way_id)] = dict(source_id=f"osm:way:{way_id}", node_refs=refs)
        self.assertEqual("reject-open-or-ambiguous-role-endpoints", self.scope.classify_relation(members, ways, nodes, {"type": "multipolygon"})[0])

    def test_orphan_hole_is_not_discarded_or_assigned_to_nearest_shell(self):
        self.assertEqual("reject-orphan-touching-or-ambiguous-inner-hole", self.classify([("outer", square(4, 4, 1)), ("inner", square(5.2, 4.2, 0.2))])[0])

    def test_hole_touching_or_crossing_outer_is_rejected(self):
        for inner in (square(4, 4.2, 0.2), square(4.9, 4.2, 0.2)):
            self.assertEqual("reject-orphan-touching-or-ambiguous-inner-hole", self.classify([("outer", square(4, 4, 1)), ("inner", inner)])[0])

    def test_hole_with_multiple_containing_outer_shells_is_ambiguous(self):
        self.assertEqual("reject-orphan-touching-or-ambiguous-inner-hole", self.classify([
            ("outer", square(4, 4, 1)), ("outer", square(4.1, 4.1, 0.8)), ("inner", square(4.2, 4.2, 0.1))])[0])

    def test_overlapping_or_nested_holes_are_not_repaired(self):
        for inner in (square(4.3, 4.3, 0.3), square(4.22, 4.22, 0.05)):
            self.assertEqual("reject-invalid-empty-or-zero-area-multipolygon", self.classify([
                ("outer", square(4, 4, 1)), ("inner", square(4.2, 4.2, 0.3)), ("inner", inner)])[0])

    def test_overlapping_outer_shells_are_not_union_repaired(self):
        self.assertEqual("reject-invalid-empty-or-zero-area-multipolygon", self.classify([
            ("outer", square(4, 4, 1)), ("outer", square(4.2, 4.2, 1))])[0])

    def test_source_hole_can_exclude_forbidden_area_without_filling_it(self):
        parts = [("outer", square(1.5, 1.5, 2)), ("inner", square(1.9, 1.9, 1.2))]
        self.assertEqual("research-qualified-complete-role-aware-multipolygon", self.classify(parts)[0])
        self.assertEqual("reject-intersects-or-near-Gaza_admin4", self.classify(parts[:1])[0])

    def test_every_outer_island_must_clear_inclusion_and_exclusions(self):
        self.assertEqual("reject-not-wholly-inside-source-IL", self.classify([
            ("outer", square(4, 4, 0.2)), ("outer", square(10.2, 4, 0.2))])[0])
        self.assertEqual("reject-intersects-or-near-Gaza_admin4", self.classify([
            ("outer", square(4, 4, 0.2)), ("outer", square(2.2, 2.2, 0.2))])[0])

    def test_boundary_contact_and_angular_margin_rejected(self):
        for outer in (square(0, 4, 0.2), square(0.0005, 4, 0.2)):
            self.assertEqual("reject-near-source-IL-boundary", self.classify([("outer", outer)])[0])

    def test_self_intersection_and_zero_area_not_repaired(self):
        for outer in ([[4, 4], [5, 5], [4, 5], [5, 4], [4, 4]], [[4, 4], [4.1, 4], [4.2, 4], [4, 4]]):
            self.assertEqual("reject-invalid-empty-or-zero-area-multipolygon", self.classify([("outer", outer)])[0])

    def test_no_outer_shell_and_area_no_rejected(self):
        self.assertEqual("reject-no-source-outer-shell", self.classify([("inner", square(4, 4, 0.2))])[0])
        args = fixture([("outer", square(4, 4, 0.2))])
        self.assertEqual("reject-not-source-area-multipolygon", self.scope.classify_relation(*args, {"type": "multipolygon", "area": "no"})[0])

    def test_research_assembly_never_changes_original_roles_or_refs(self):
        args = fixture([("outer", square(4, 4, 1)), ("inner", square(4.2, 4.2, 0.2))])
        before = copy.deepcopy(args)
        decision, geometry = self.scope.classify_relation(*args, {"type": "multipolygon"})
        self.assertEqual("research-qualified-complete-role-aware-multipolygon", decision)
        self.assertEqual(before, args)
        self.assertEqual(2, sum(len(r["member_traversal"]) for role in ("outer", "inner") for r in geometry[role]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
